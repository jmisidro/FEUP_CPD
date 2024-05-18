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
    private final int ranked; // 0 - Simple Mode, 1 - Ranked Mode
    private long startTime; // Server start time
    private ServerSocketChannel serverSocket;
    private final ExecutorService gameThreadPool;
    private final ExecutorService authThreadPool;
    private final ReentrantLock timeLock;

    // Ping
    private long lastPing; // Last time the server pinged the players

    // Database
    private Database database;
    private ReentrantLock databaseLock;

    // Players
    private List<Player> waitingQueue;
    private ReentrantLock waitingQueueLock;

    // Token Generation
    private int tokenIdx;
    private final ReentrantLock tokenLock;

    // Server Menu
    private final ServerMenu serverMenu;

    // Constants
    private final String DATABASE_PATH = "server/";
    private final int SLACK_FACTOR = 4; // Slack Factor, used to decrease the waiting times for ranked games
    private final int MAX_CONCURRENT_AUTH = 5; // Maximum number of concurrent authentications
    private final int PLAYERS_PER_GAME = 2; // Number of players per game
    private final int MAX_CONCURRENT_GAMES = 3; // Maximum number of concurrent games


    public Server(int port, int ranked, String filename) throws IOException, ParseException {

        // Server information
        this.port = port;
        this.ranked = ranked;
        this.startTime = System.currentTimeMillis();

        // Concurrent fields
        this.gameThreadPool = Executors.newFixedThreadPool(MAX_CONCURRENT_GAMES);
        this.authThreadPool = Executors.newFixedThreadPool(MAX_CONCURRENT_AUTH);
        this.waitingQueue = new ArrayList<Player>();
        this.database = new Database(this.DATABASE_PATH + filename);
        this.tokenIdx = 0;

        // Locks
        this.waitingQueueLock = new ReentrantLock();
        this.databaseLock = new ReentrantLock();
        this.tokenLock = new ReentrantLock();
        this.timeLock = new ReentrantLock();

        // Server Menu
        this.serverMenu = new ServerMenu(ranked);
        this.lastPing = System.currentTimeMillis();
    }

    /*
     * Prints the usage of the Server program
     */
    public static void printUsage() {
        String usage = """
                usage: java Server <PORT> <DATABASE> [MODE]
                       <DATABASE>
                           JSON file name inside server folder (e.g. database.json)
                       [MODE]
                           0 - Simple Mode
                           1 - Ranked Mode""";
        System.out.println(usage);
    }

    /*
     * Starts the server
     */
    public void start() throws IOException {
        serverSocket = ServerSocketChannel.open();
        serverSocket.bind(new InetSocketAddress(port));
        String mode = ranked == 1 ? "ranked" : "simple";
        System.out.println("Server is handleServerMessages on port " + port + " with " + mode + " mode");
    }

    /*
     * Resets the time and updates the start time
     */
    private void resetStartTime() {
        timeLock.lock();
        startTime = System.currentTimeMillis();
        timeLock.unlock();
    }

    /*
     * Calculates the slack time for the server to increase the tolerated interval
     */
    private int calculateSlack() {
        timeLock.lock();
        int time = (int) ((System.currentTimeMillis() - startTime) / 1000);
        int slack = time * SLACK_FACTOR;
        timeLock.unlock();
        return slack;
    }

    /*
     * Schedule games by creating a new game with players from the waiting queue
     */
    private void scheduleSimpleGame() {
        waitingQueueLock.lock();

        if (waitingQueue.size() >= PLAYERS_PER_GAME) {
            // Create a list of players for the game
            List<Player> gamePlayers = new ArrayList<>(waitingQueue.subList(0, PLAYERS_PER_GAME));
            // Remove the players from the waiting queue
            waitingQueue.removeAll(gamePlayers);

            gamePlayers.forEach(player -> System.out.println("Player " + player.getUsername() + " removed from waiting queue"));

            // Start the game
            Runnable gameRunnable = new Game(gamePlayers, database, databaseLock, waitingQueue, waitingQueueLock);
            gameThreadPool.execute(gameRunnable);
        }
        updateServerMenu();

        waitingQueueLock.unlock();
    }

    /*
     * Schedule games by creating a new game with players from the waiting queue with similar rankings
     */
    private void scheduleRankedGame() {
        waitingQueueLock.lock();
        if (waitingQueue.size() >= PLAYERS_PER_GAME) {
            sortPlayers(); // Sort the players by rank
            int slack = calculateSlack(); // Calculate the slack
            for (int i = 0; i <= waitingQueue.size() - PLAYERS_PER_GAME; i++) {
                // calculate absolute difference between the first and last player in the game
                int rankDifference = (int) Math.abs(waitingQueue.get(i).getRank() - waitingQueue.get(i + PLAYERS_PER_GAME - 1).getRank());
                // if the difference is less than the slack, create a game
                if (rankDifference < slack) {
                    List<Player> players = new ArrayList<>(waitingQueue.subList(i, i + PLAYERS_PER_GAME));
                    waitingQueue.removeAll(players);
                    gameThreadPool.execute(new Game(players, database, databaseLock, waitingQueue, waitingQueueLock));
                    resetStartTime();
                    break;
                }
            }
        }
        updateServerMenu();
        waitingQueueLock.unlock();
    }

    /*
     * Handles the authentication of new players
     */
    private void connectionAuthenticator() {
        while (true) {
            try {
                SocketChannel playerSocket = serverSocket.accept();
                handleNewPlayer(playerSocket);
            } catch (Exception exception) {
                System.out.println("Error handling new player: " + exception);
            }
        }
    }

    /*
     * Handles a new player connection
     * @param playerSocket: SocketChannel to send the request
     */
    private void handleNewPlayer(SocketChannel playerSocket) throws IOException {
        System.out.println("Player connected: " + playerSocket.getRemoteAddress());
        Runnable newPlayerRunnable = () -> {
            try {
                handlePlayer(playerSocket);
            } catch (Exception exception) {
                System.out.println("Error handling new player: " + exception);
            }
        };
        this.authThreadPool.execute(newPlayerRunnable);
    }

    /*
     * Pings the players in the waiting queue
     */
    private void pingPlayers() {
        // Time between pings to players (milliseconds)
        int PING_INTERVAL = 15000;
        if(System.currentTimeMillis() - lastPing > PING_INTERVAL) {
            lastPing = System.currentTimeMillis();

            waitingQueueLock.lock();
            if (waitingQueue.isEmpty()) {
                waitingQueueLock.unlock();
                return;
            }

            System.out.println("Pinging players...");

            // Ping all players in the waiting queue
            Iterator<Player> iterator = waitingQueue.iterator();
            while (iterator.hasNext()) {
                Player player = iterator.next();
                try {
                    Server.request(player.getSocket(), "PING", "");
                } catch (IOException exception) {
                    System.out.println("Error pinging player: " + exception);
                    iterator.remove();
                } catch (Exception e) {
                    waitingQueueLock.unlock();
                    throw new RuntimeException(e);
                }
            }
            waitingQueueLock.unlock();
        }
    }

    /*
     * Runs the server
     */
    public void run() throws IOException {

        // Keeps an eye on the waiting list and launches a new game
        // whenever it can, according to the threadPoll
        Thread gameSchedulerThread = new Thread(() -> {
            while (true) {
                pingPlayers();
                if (ranked == 1)
                    scheduleRankedGame();
                else
                    scheduleSimpleGame();
            }
        });

        // Authenticates all connections and push new players into waiting list
        Thread connectionAuthenticatorThread = new Thread(() -> {
            while (true) connectionAuthenticator();
        });

        // Resets the saved player tokens before starting the server
        databaseLock.lock();
        database.resetTokens();
        databaseLock.unlock();

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
        tokenLock.lock();
        int index = tokenIdx;
        tokenIdx++;
        tokenLock.unlock();
        return BCrypt.hashpw(username + index, BCrypt.gensalt());
    }

    /*
     * Adds a player to the waiting queue
     * @param player: Player object to insert in the queue
     * @return void
     */
    private void addPlayer(Player player) {
        waitingQueueLock.lock();
        try {
            Player existingPlayer = waitingQueue.stream()
                    .filter(p -> p.equals(player))
                    .findFirst()
                    .orElse(null);

            if (existingPlayer != null) {
                existingPlayer.setSocket(player.getSocket());
                System.out.println("Player " + player.getUsername() + " reconnected. Queue size: " + waitingQueue.size());
                Server.request(player.getSocket(), "QUEUE", "You are already in the waiting queue with " + player.getRank() + " points.");
            } else {
                waitingQueue.add(player);
                System.out.println("Player " + player.getUsername() + " is now in waiting queue. Queue size: " + waitingQueue.size());
                Server.request(player.getSocket(), "QUEUE", "You entered in waiting queue with ranking  " + player.getRank() + " points.");
            }
            Connection.receive(player.getSocket());
        } catch (Exception exception) {
            System.out.println("Error during insert in waiting queue. Info: " + exception.getMessage());
        } finally {
            waitingQueueLock.unlock();
        }
    }

    /*
     * Sorts the waiting queue by rank
     */
    private void sortPlayers() {
        waitingQueueLock.lock();
        waitingQueue.sort(Comparator.comparingLong(Player::getRank));
        waitingQueueLock.unlock();
    }

    /*
     * Authenticates a player
     * @param playerSocket: SocketChannel to send the request
     * @param username: Username
     * @param password: Password
     * @return Player object if the authentication is successful, null otherwise
     */
    public Player login(SocketChannel playerSocket, String username, String password) throws Exception {

        if (Objects.equals(username, "BACK") || Objects.equals(password, "BACK"))
            return null;

        String token = this.getToken(username);
        Player player;

        try {
            databaseLock.lock();
            player = database.login(username, password, token, playerSocket);
            database.backup();
            databaseLock.unlock();

            if (player != null) {
                Server.request(playerSocket, "AUTH", "token-" + username + ".txt\n" + token);
                Connection.receive(playerSocket);
                return player;
            } else {
                Server.request(playerSocket, "NACK", "Wrong username or password");
                Connection.receive(playerSocket);
            }

        } catch (Exception e) {
            Server.request(playerSocket, "NACK", e.getMessage());
            Connection.receive(playerSocket);
        }
        return null;
    }

    /*
     * Registers a new player
     * @param playerSocket: SocketChannel to send the request
     * @param username: Username
     * @param password: Password
     * @return Player object if the registration is successful, null otherwise
     */
    public Player register(SocketChannel playerSocket, String username, String password) throws Exception {

        if (Objects.equals(username, "BACK") || Objects.equals(password, "BACK"))
            return null;

        String token = this.getToken(username);
        Player player;

        try {
            databaseLock.lock();
            player = database.register(username, password, token, playerSocket);
            database.backup();
            databaseLock.unlock();

            if (player != null) {
                Server.request(playerSocket, "AUTH", "token-" + username + ".txt\n" + token);
                Connection.receive(playerSocket);
                return player;
            } else {
                Server.request(playerSocket, "NACK", "Username already in use");
                Connection.receive(playerSocket);
            }

        } catch (Exception e) {
            Server.request(playerSocket, "NACK", e.getMessage());
            Connection.receive(playerSocket);
        }
        return null;
    }

    /*
     * Restores a player connection
     * @param playerSocket: SocketChannel to send the request
     * @param token: Session token
     * @return Player object if the restoration is successful, null otherwise
     */
    public Player restore(SocketChannel playerSocket, String token) throws Exception {

        databaseLock.lock();
        Player player = database.restore(token, playerSocket);
        database.backup();
        databaseLock.unlock();

        if (player != null) {
            Server.request(playerSocket, "AUTH", "token-" + player.getUsername() + ".txt\n" + token);
            Connection.receive(playerSocket);
        } else {
            Server.request(playerSocket, "NACK","Invalid session token");
            Connection.receive(playerSocket);
        }
        return player;
    }

    /*
    * Sends a request to the player
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
     * Handles a new player connection
     */
    public void handlePlayer(SocketChannel playerSocket) throws Exception {
        String input;
        Player player = null;
        long startTime = System.currentTimeMillis();

        do {
            int TIMEOUT = 30000;
            if (System.currentTimeMillis() - startTime >= TIMEOUT) {
                terminateConnection(playerSocket, "Connection timeout");
                return;
            }

            input = getOptionFromPlayer(playerSocket);

            switch (input) {
                case "1" -> player = authenticatePlayer(playerSocket, "login");
                case "2" -> player = authenticatePlayer(playerSocket, "register");
                case "3" -> {
                    Server.request(playerSocket, "TKN", "Token?");
                    String token = Connection.receive(playerSocket);
                    System.out.println("TOKEN: " + token);
                    if (token.equals("BACK")) continue;
                    player = restore(playerSocket, token);
                }
                default -> {
                    terminateConnection(playerSocket, "Connection terminated");
                    return;
                }
            }

            if (player != null) {
                this.addPlayer(player);
                if (ranked == 1) {
                    sortPlayers();
                    resetStartTime();
                }
            }

        } while (player == null);
        updateServerMenu();
    }

    /*
     * Terminates the connection with the player
     * @param playerSocket: SocketChannel to send the request
     * @param message: Message to send
     */
    private void terminateConnection(SocketChannel playerSocket, String message) throws Exception {
        System.out.println("Connection timeout");
        Server.request(playerSocket, "END", message);
        playerSocket.close();
    }

    /*
     * Gets the option from the player
     * @param playerSocket: SocketChannel to send the request
     * @return Option chosen by the player
     */
    private String getOptionFromPlayer(SocketChannel playerSocket) throws Exception {
        Server.request(playerSocket, "OPT", "1 - Login\n2 - Register\n3 - Restore Connection\n4 - Quit");
        return Connection.receive(playerSocket).toUpperCase();
    }

    /*
     * Authenticates a player
     * @param playerSocket: SocketChannel to send the request
     * @param authenticationType: Type of authentication
     * @return Player object if the authentication is successful, null otherwise
     */
    private Player authenticatePlayer(SocketChannel playerSocket, String authenticationType) throws Exception {
        Server.request(playerSocket, "USR", "Username?");
        String username = Connection.receive(playerSocket);
        if (username.equals("BACK")) return null;
        Server.request(playerSocket, "PSW", "Password?");
        String password = Connection.receive(playerSocket);

        return switch (authenticationType) {
            case "login" -> login(playerSocket, username, password);
            case "register" -> register(playerSocket, username, password);
            default -> null;
        };
    }

    /*
     * Updates the server menu
     */
    public void updateServerMenu() {
        int totalGames = ((ThreadPoolExecutor) gameThreadPool).getActiveCount();
        serverMenu.setGames(String.valueOf(totalGames));

        waitingQueueLock.lock();
        try {
            String[] waitingQueueUsernames = waitingQueue.stream()
                    .limit(MAX_CONCURRENT_AUTH)
                    .map(Player::getUsername)
                    .toArray(String[]::new);
            serverMenu.setQueue(String.valueOf(waitingQueue.size()), waitingQueueUsernames);
        } finally {
            waitingQueueLock.unlock();
        }

        databaseLock.lock();
        try {
            // Update the leaderboard to display the top 3 players
            serverMenu.setLeaderboard(database.getLeaderboard(3));
        } finally {
            databaseLock.unlock();
        }
    }

    public static void main(String[] args) {

        // Check if the number of arguments is correct
        if (args.length < 2 || args.length > 3) {
            Server.printUsage();
            return;
        }

        // Parse the arguments
        int port = Integer.parseInt(args[0]);
        String filename = args[1];
        int ranked = args.length == 3 ? Integer.parseInt(args[2]) : 0; // Default to simple mode

        if (ranked != 0 && ranked != 1) {
            Server.printUsage();
            return;
        }

        // Start the connection
        try {
            Server server = new Server(port, ranked, filename);
            server.start();
            server.run();
        } catch (IOException | ParseException exception) {
            System.out.println("Server exception: " + exception.getMessage());
        }
    }
}