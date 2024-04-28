import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.simple.parser.ParseException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.security.crypto.bcrypt.BCrypt;

public class Server {

    // Server
    private final int port;
    private final int mode;
    private ServerSocketChannel serverSocket;
    private final ExecutorService threadPoolGame;
    private final ExecutorService threadPoolAuth;
    private int time;
    private long startTime;
    private final ReentrantLock time_lock;

    private long lastPing;

    // Represents the time, in seconds, for the server to increase the tolerated interval
    // between players with different rankings
    private final int TIME_FACTOR = 1;

    // Database
    private Database database;
    private ReentrantLock database_lock;
    private final String DATABASE_PATH = "assign2/server/";

    // Players
    private List<Player> waiting_queue;
    private ReentrantLock waiting_queue_lock;

    private final int MAX_CONCURRENT_AUTH = 5; // Maximum number of concurrent authentications

    // Token Generation
    private int token_index;
    private final ReentrantLock token_lock;

    // Server Menu
    private final ServerMenu serverMenu;


    public Server(int port, int mode, String filename) throws IOException, ParseException {

        // Server information
        this.port = port;
        this.mode = mode;
        this.startTime = System.currentTimeMillis();

        // Concurrent fields
        // Game
        int MAX_CONCURRENT_GAMES = 3; // Maximum number of concurrent games
        this.threadPoolGame = Executors.newFixedThreadPool(MAX_CONCURRENT_GAMES);
        this.threadPoolAuth = Executors.newFixedThreadPool(MAX_CONCURRENT_AUTH);
        this.waiting_queue = new ArrayList<Player>();
        this.database = new Database(this.DATABASE_PATH + filename);
        this.token_index = 0;
        this.time = 0;

        // Locks
        this.waiting_queue_lock = new ReentrantLock();
        this.database_lock = new ReentrantLock();
        this.token_lock = new ReentrantLock();
        this.time_lock = new ReentrantLock();

        // Server Menu
        this.serverMenu = new ServerMenu();
        this.lastPing = System.currentTimeMillis();
    }

    /*
     * Prints the usage of the Server class
     */
    public static void printUsage() {
        String usage = "usage: java Server <PORT> <MODE> <DATABASE>\n" +
                "       <MODE>\n" +
                "           0 - Simple Mode\n" +
                "           1 - Ranked Mode\n" +
                "       <DATABASE>\n" +
                "           JSON file name inside server folder (e.g. database.json)";
        System.out.println(usage);
    }

    /*
     * Starts the server
     */
    public void start() throws IOException {
        serverSocket = ServerSocketChannel.open();
        serverSocket.bind(new InetSocketAddress(port));
        String mode = this.mode == 1 ? "ranked" : "simple";
        System.out.println("Server is listening on port " + port + " with " + mode + " mode");
    }

    /*
     * Resets the time
     */
    private void resetTime() {
        time_lock.lock();
        startTime = System.currentTimeMillis();
        time = 0;
        time_lock.unlock();
    }

    /*
     * Schedule games by creating a new game with players from the waiting queue
     */
    private void scheduleSimpleGame() {
        waiting_queue_lock.lock();

        int PLAYERS_PER_GAME = 2;
        if (waiting_queue.size() >= PLAYERS_PER_GAME) {
            List<Player> gamePlayers = new ArrayList<>(waiting_queue.subList(0, PLAYERS_PER_GAME));
            waiting_queue.removeAll(gamePlayers);
            gamePlayers.forEach(player -> System.out.println("Player " + player.getUsername() + " removed from waiting queue"));

            Runnable gameRunnable = new Game(gamePlayers, database, database_lock, waiting_queue, waiting_queue_lock);
            threadPoolGame.execute(gameRunnable);
        }
        updateServerMenu();

        waiting_queue_lock.unlock();
    }

    /*
     * Handles the authentication of new clients
     */
    private void connectionAuthenticator() {
        while (true) {
            try {
                SocketChannel clientSocket = serverSocket.accept();
                System.out.println("Player connected: " + clientSocket.getRemoteAddress());

                Runnable newClientRunnable = () -> {
                    try {
                        handleClient(clientSocket);
                    } catch (Exception exception) {
                        System.out.println("Error handling new client: " + exception);
                    }
                };
                this.threadPoolAuth.execute(newClientRunnable);

            } catch (Exception exception) {
                System.out.println("Error handling new client: " + exception);
            }
        }
    }

    /*
     * Pings the clients in the waiting queue
     */
    private void pingClients() {
        // Time between pings to clients (milliseconds)
        int PING_INTERVAL = 10000;
        if(System.currentTimeMillis() - lastPing > PING_INTERVAL) {
            lastPing = System.currentTimeMillis();

            waiting_queue_lock.lock();
            if (waiting_queue.isEmpty()) {
                waiting_queue_lock.unlock();
                return;
            }

            System.out.println("Pinging clients...");

            Iterator<Player> iterator = waiting_queue.iterator();
            while (iterator.hasNext()) {
                Player player = iterator.next();
                try {
                    Server.request(player.getSocket(), "PING", "");
                } catch (IOException exception) {
                    System.out.println("Error pinging player: " + exception);
                    iterator.remove();
                } catch (Exception e) {
                    waiting_queue_lock.unlock();
                    throw new RuntimeException(e);
                }
            }
            waiting_queue_lock.unlock();
        }
    }


    public void run() throws IOException {

        // Keeps an eye on the waiting list and launches a new game
        // whenever it can, according to the threadPoll
        Thread gameSchedulerThread = new Thread(() -> {
            while (true) {
                pingClients();
                if (mode == 0)
                    scheduleSimpleGame();
                else
                    scheduleSimpleGame(); // TODO: Implement gameSchedulerRanked
            }
        });

        // Authenticates all connections and push new clients into waiting list
        Thread connectionAuthenticatorThread = new Thread(() -> {
            while (true) connectionAuthenticator();
        });

        // Resets the saved player tokens before starting the server
        database_lock.lock();
        database.resetTokens();
        database_lock.unlock();

        // Run threads
        gameSchedulerThread.start();
        connectionAuthenticatorThread.start();
    }

    /*
     * Generates a session token, using BCrypt
     * @param username: Username
     * @return Session token
     */
    private String getToken(String username) {
        token_lock.lock();
        int index = token_index;
        token_index++;
        token_lock.unlock();
        return BCrypt.hashpw(username + index, BCrypt.gensalt());
    }

    /*
     * Adds a player to the waiting queue
     * @param player: Player object to insert in the queue
     * @return void
     */
    private void addPlayer(Player player) {
        waiting_queue_lock.lock();
        try {
            Player existingPlayer = waiting_queue.stream()
                    .filter(p -> p.equals(player))
                    .findFirst()
                    .orElse(null);

            if (existingPlayer != null) {
                existingPlayer.setSocket(player.getSocket());
                System.out.println("Player " + player.getUsername() + " reconnected. Queue size: " + waiting_queue.size());
                Server.request(player.getSocket(), "QUEUE", "You are already in the waiting queue with " + player.getRank() + " points.");
            } else {
                waiting_queue.add(player);
                System.out.println("Player " + player.getUsername() + " is now in waiting queue. Queue size: " + waiting_queue.size());
                Server.request(player.getSocket(), "QUEUE", "You entered in waiting queue with ranking  " + player.getRank() + " points.");
            }
            Connection.receive(player.getSocket());
        } catch (Exception exception) {
            System.out.println("Error during insert in waiting queue. Info: " + exception.getMessage());
        } finally {
            waiting_queue_lock.unlock();
        }
    }

    /*
     * Sorts the waiting queue by rank
     */
    private void sortPlayers() {
        waiting_queue_lock.lock();
        waiting_queue.sort(Comparator.comparingLong(Player::getRank));
        waiting_queue_lock.unlock();
    }

    /*
     * Authenticates a client
     * @param clientSocket: SocketChannel to send the request
     * @param username: Username
     * @param password: Password
     * @return Player object if the authentication is successful, null otherwise
     */
    public Player login(SocketChannel clientSocket, String username, String password) throws Exception {

        if (Objects.equals(username, "BACK") || Objects.equals(password, "BACK"))
            return null;

        String token = this.getToken(username);
        Player player;

        try {
            database_lock.lock();
            player = database.login(username, password, token, clientSocket);
            database.backup();
            database_lock.unlock();

            if (player != null) {
                Server.request(clientSocket, "AUTH", "token-" + username + ".txt\n" + token);
                Connection.receive(clientSocket);
                return player;
            } else {
                Server.request(clientSocket, "NACK", "Wrong username or password");
                Connection.receive(clientSocket);
            }

        } catch (Exception e) {
            Server.request(clientSocket, "NACK", e.getMessage());
            Connection.receive(clientSocket);
        }
        return null;
    }

    /*
     * Registers a new client
     * @param clientSocket: SocketChannel to send the request
     * @param username: Username
     * @param password: Password
     * @return Player object if the registration is successful, null otherwise
     */
    public Player register(SocketChannel clientSocket, String username, String password) throws Exception {

        if (Objects.equals(username, "BACK") || Objects.equals(password, "BACK"))
            return null;

        String token = this.getToken(username);
        Player player;

        try {
            database_lock.lock();
            player = database.register(username, password, token, clientSocket);
            database.backup();
            database_lock.unlock();

            if (player != null) {
                Server.request(clientSocket, "AUTH", "token-" + username + ".txt\n" + token);
                Connection.receive(clientSocket);
                return player;
            } else {
                Server.request(clientSocket, "NACK", "Username already in use");
                Connection.receive(clientSocket);
            }

        } catch (Exception e) {
            Server.request(clientSocket, "NACK", e.getMessage());
            Connection.receive(clientSocket);
        }
        return null;
    }

    /*
     * Restores a client connection
     * @param clientSocket: SocketChannel to send the request
     * @param token: Session token
     * @return Player object if the restoration is successful, null otherwise
     */
    public Player restore(SocketChannel clientSocket, String token) throws Exception {

        database_lock.lock();
        Player player = database.restore(token, clientSocket);
        database.backup();
        database_lock.unlock();

        if (player != null) {
            Server.request(clientSocket, "AUTH", "token-" + player.getUsername() + ".txt\n" + token);
            Connection.receive(clientSocket);
        } else {
            Server.request(clientSocket, "NACK","Invalid session token");
            Connection.receive(clientSocket);
        }
        return player;
    }

    /*
    * Sends a request to the client
    * @param socket: SocketChannel to send the request
    * @param requestType: Type of request
    *  - END: Connection terminated -> receives ACK
    *  - TKN: Session token value -> receives a session token
    *  - USR: Username -> receives username
    *  - PSW: Password -> receives password
    *  - OPT: Menu options -> receives option
    *  - AUTH: tokenName + tokenName -> receives ACK
    *  - INFO: Message -> receives ACK
    *  - NACK: Error -> receives ACK
    *  - TURN: Message -> receives input
    * @param message: Message to send
    */
    public static void request(SocketChannel socket, String requestType, String message) throws Exception {
        Connection.send(socket, requestType + "\n" + message);
    }

    /*
     * Handles a new client connection
     */
    public void handleClient(SocketChannel clientSocket) throws Exception {

        String input;
        Player player = null;
        long startTime = System.currentTimeMillis();

        do {
            // Timeout to avoid slow clients in authentication (milliseconds)
            int TIMEOUT = 30000;
            // Check if timeout has been reached
            if (System.currentTimeMillis() - startTime >= TIMEOUT) {
                System.out.println("Connection timeout");
                Server.request(clientSocket, "END", "Connection terminated");
                return;
            }

            // Login, register, restore and quit choosing options
            Server.request(clientSocket, "OPT", "1 - Login\n2 - Register\n3 - Restore Connection\n4 - Quit");
            input = Connection.receive(clientSocket).toUpperCase();

            // Authentication protocol
            String username, password, token;
            switch (input) {
                case "1" -> {
                    Server.request(clientSocket, "USR", "Username?");
                    username = Connection.receive(clientSocket);
                    System.out.println(username);
                    if (username.equals("BACK")) continue;
                    Server.request(clientSocket, "PSW", "Password?");
                    password = Connection.receive(clientSocket);
                    player = this.login(clientSocket, username, password);
                }
                case "2" -> {
                    Server.request(clientSocket, "USR", "Username?");
                    username = Connection.receive(clientSocket);
                    System.out.println(username);
                    if (username.equals("BACK")) continue;
                    Server.request(clientSocket, "PSW", "Password?");
                    password = Connection.receive(clientSocket);
                    player = this.register(clientSocket, username, password);
                }
                case "3" -> {
                    Server.request(clientSocket, "TKN", "Token?");
                    token = Connection.receive(clientSocket);
                    System.out.println("TOKEN: " + token);
                    if (token.equals("BACK")) continue;
                    player = this.restore(clientSocket, token);
                }
                default -> {
                    Server.request(clientSocket, "END", "Connection terminated");
                    clientSocket.close();
                    return;
                }
            }

            // Deal with waiting queue
            if (player != null) {
                this.addPlayer(player);
                if (mode == 1) {
                    sortPlayers();
                    resetTime();
                }
            }

        } while (player == null);
        updateServerMenu();
    }

    /*
     * Updates the server menu
     */
    public void updateServerMenu() {
        int totalGames = ((ThreadPoolExecutor) threadPoolGame).getActiveCount();
        serverMenu.setGames(String.valueOf(totalGames));

        waiting_queue_lock.lock();
        try {
            String[] waitingQueueUsernames = waiting_queue.stream()
                    .limit(MAX_CONCURRENT_AUTH)
                    .map(Player::getUsername)
                    .toArray(String[]::new);
            serverMenu.setQueue(String.valueOf(waiting_queue.size()), waitingQueueUsernames);
        } finally {
            waiting_queue_lock.unlock();
        }

        database_lock.lock();
        try {
            // Update the leaderboard to display the top 3 players
            serverMenu.setLeaderboard(database.getLeaderboard(3));
        } finally {
            database_lock.unlock();
        }
    }

    public static void main(String[] args) {

        // Check if there are enough arguments
        if (args.length != 3) {
            Server.printUsage();
            return;
        }

        // Parse port and host arguments and create a Server object
        int port = Integer.parseInt(args[0]);
        int mode = Integer.parseInt(args[1]);
        String filename = args[2];
        if (mode != 0 && mode != 1) {
            Server.printUsage();
            return;
        }

        // Start the connection
        try {
            Server server = new Server(port, mode, filename);
            server.start();
            server.run();
        } catch (IOException | ParseException exception) {
            System.out.println("Server exception: " + exception.getMessage());
        }
    }
}