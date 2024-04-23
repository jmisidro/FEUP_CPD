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

    // Timeouts
    private final int TIMEOUT = 30000;          // Timeout to avoid slow clients in authentication (milliseconds)
    private final int PING_INTERVAL = 10000;    // Time between pings to clients (milliseconds)
    private long lastPing;

    // Game
    private final int MAX_CONCURRENT_GAMES = 2;
    private final int PLAYERS_PER_GAME = 5;

    // Represents the time, in seconds, for the server to increase the tolerated interval
    // between players with different rankings
    private final int TIME_FACTOR = 1;

    // Database
    private Database database;
    private ReentrantLock database_lock;
    private final String DATABASE_PATH = "assign2/src/server/";

    // Clients
    private List<Client> waiting_queue;
    private ReentrantLock waiting_queue_lock;
    private final int MAX_CONCURRENT_AUTH = 5;

    // Token Generation
    private int token_index;
    private ReentrantLock token_lock;

    // GUI
    private final ServerGUI serverGUI;


    public Server(int port, int mode, String filename) throws IOException, ParseException {

        // Server information
        this.port = port;
        this.mode = mode;
        this.startTime = System.currentTimeMillis();

        // Concurrent fields
        this.threadPoolGame = Executors.newFixedThreadPool(this.MAX_CONCURRENT_GAMES);
        this.threadPoolAuth = Executors.newFixedThreadPool(this.MAX_CONCURRENT_AUTH);
        this.waiting_queue = new ArrayList<Client>();
        this.database = new Database(this.DATABASE_PATH + filename);
        this.token_index = 0;
        this.time = 0;

        // Locks
        this.waiting_queue_lock = new ReentrantLock();
        this.database_lock = new ReentrantLock();
        this.token_lock = new ReentrantLock();
        this.time_lock = new ReentrantLock();

        // Server GUI
        this.serverGUI = new ServerGUI();
        this.lastPing = System.currentTimeMillis();
    }

    // Server usage
    public static void printUsage() {
        System.out.println("usage: java Server <PORT> <MODE> <DATABASE>");
        System.out.println("       <MODE>");
        System.out.println("           0 - Simple Mode");
        System.out.println("           1 - Ranked Mode");
        System.out.println("       <DATABASE>");
        System.out.println("           JSON file name inside server folder (e.g. database.json)");
    }

    // Starts the server and listens for connections on the specified port
    public void start() throws IOException {
        this.serverSocket = ServerSocketChannel.open();
        serverSocket.bind(new InetSocketAddress(this.port));
        String mode = this.mode == 1 ? "ranked" : "simple";
        System.out.println("Server is listening on port " + this.port + " with " + mode + " mode");
    }

    // Resets the server time: time and startTime
    private void resetTime() {
        this.time_lock.lock();
        this.startTime = System.currentTimeMillis();
        this.time = 0;
        this.time_lock.unlock();
    }

    // Handle incoming client connections
    private void connectionAuthenticator() {
        while (true) {
            try {
                SocketChannel clientSocket = this.serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getRemoteAddress());

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


    public void run() throws IOException {

        // Authenticates all connections and push new clients into waiting list
        Thread connectionAuthenticatorThread = new Thread(() -> {
            while (true) connectionAuthenticator();
        });

        // Resets the saved player tokens before starting the server
        this.database_lock.lock();
        this.database.resetTokens();
        this.database_lock.unlock();

        // Run threads
        // TODO: Start the game thead
        connectionAuthenticatorThread.start();
    }

    /*
     * Generates a session token, using BCrypt
     * @param username: Username
     * @return Session token
     */
    private String getToken(String username) {
        this.token_lock.lock();
        int index = this.token_index;
        this.token_index++;
        this.token_lock.unlock();
        return BCrypt.hashpw(username + index, BCrypt.gensalt());
    }

    /*
     * Inserts a client in the waiting queue
     * @param client: Client object to insert in the queue
     * @return void
     */
    private void insertClient(Client client) {

        try {
            this.waiting_queue_lock.lock();
            for (Client c : this.waiting_queue) {
                if (c.equals(client)) {
                    // If the client is already in the queue, their socket is updated with the new one
                    c.setSocket(client.getSocket());
                    System.out.println("Client " + client.getUsername() + " reconnected. Queue size: " + this.waiting_queue.size());
                    Server.request(client.getSocket(), "QUEUE", "You are already in the waiting queue with " + client.getRank() + " points.");
                    Connection.receive(client.getSocket());
                    this.waiting_queue_lock.unlock();
                    return;
                }
            }

            // If the client is not already in the queue, add them to the end of the queue
            this.waiting_queue.add(client);
            Server.request(client.getSocket(), "QUEUE", "You entered in waiting queue with ranking  " + client.getRank() + " points.");
            Connection.receive(client.getSocket());
            System.out.println("Client " + client.getUsername() + " is now in waiting queue. Queue size: " + this.waiting_queue.size());

        } catch (Exception exception) {
            System.out.println("Error during insert in waiting queue. Info: " + exception.getMessage());
        } finally {
            this.waiting_queue_lock.unlock();
        }
    }

    /*
     * Sorts the waiting queue by rank
     */
    private void sortClients() {
        this.waiting_queue_lock.lock();
        this.waiting_queue.sort(Comparator.comparingLong(Client::getRank));
        this.waiting_queue_lock.unlock();
    }

    /*
     * Authenticates a client
     * @param clientSocket: SocketChannel to send the request
     * @param username: Username
     * @param password: Password
     * @return Client object if the authentication is successful, null otherwise
     */
    public Client login(SocketChannel clientSocket, String username, String password) throws Exception {

        if (Objects.equals(username, "BACK") || Objects.equals(password, "BACK"))
            return null;

        String token = this.getToken(username);
        Client client;

        try {
            this.database_lock.lock();
            client = this.database.login(username, password, token, clientSocket);
            this.database.backup();
            this.database_lock.unlock();

            if (client != null) {
                Server.request(clientSocket, "AUTH", "token-" + username + ".txt\n" + token);
                Connection.receive(clientSocket);
                return client;
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
     * @return Client object if the registration is successful, null otherwise
     */
    public Client register(SocketChannel clientSocket, String username, String password) throws Exception {

        if (Objects.equals(username, "BACK") || Objects.equals(password, "BACK"))
            return null;

        String token = this.getToken(username);
        Client client;

        try {
            this.database_lock.lock();
            client = this.database.register(username, password, token, clientSocket);
            this.database.backup();
            this.database_lock.unlock();

            if (client != null) {
                Server.request(clientSocket, "AUTH", "token-" + username + ".txt\n" + token);
                Connection.receive(clientSocket);
                return client;
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
     * @return Client object if the restoration is successful, null otherwise
     */
    public Client restore(SocketChannel clientSocket, String token) throws Exception {

        this.database_lock.lock();
        Client client = this.database.restore(token, clientSocket);
        this.database.backup();
        this.database_lock.unlock();

        if (client != null) {
            Server.request(clientSocket, "AUTH", "token-" + client.getUsername() + ".txt\n" + token);
            Connection.receive(clientSocket);
        } else {
            Server.request(clientSocket, "NACK","Invalid session token");
            Connection.receive(clientSocket);
        }
        return client;
    }

    /*
    * Sends a request to the client
    * @param socket: SocketChannel to send the request
    * @param requestType: Type of request
    *  - END: Connection terminated
    *  - TKN: Session token value
    *  - USR: Username
    *  - PSW: Password
    *  - OPT: Menu
    *  - AUTH: tokenName + tokenName
    *  - INFO: Message
    *  - NACK: Error
    *  - TURN: Message
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
        Client client = null;
        long startTime = System.currentTimeMillis();

        do {
            // Check if timeout has been reached
            if (System.currentTimeMillis() - startTime >= this.TIMEOUT) {
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
                    client = this.login(clientSocket, username, password);
                }
                case "2" -> {
                    Server.request(clientSocket, "USR", "Username?");
                    username = Connection.receive(clientSocket);
                    System.out.println(username);
                    if (username.equals("BACK")) continue;
                    Server.request(clientSocket, "PSW", "Password?");
                    password = Connection.receive(clientSocket);
                    client = this.register(clientSocket, username, password);
                }
                case "3" -> {
                    Server.request(clientSocket, "TKN", "Token?");
                    token = Connection.receive(clientSocket);
                    System.out.println("TOKEN: " + token);
                    if (token.equals("BACK")) continue;
                    client = this.restore(clientSocket, token);
                }
                default -> {
                    Server.request(clientSocket, "END", "Connection terminated");
                    clientSocket.close();
                    return;
                }
            }

            // Deal with waiting queue
            if (client != null) {
                this.insertClient(client);
                if (this.mode == 1) {
                    this.sortClients();
                    this.resetTime();
                }
            }

        } while (client == null);
        updateServerGUI();
    }

    /*
     * Updates the server time and increases the time interval between players with different ranks
     */
    public void updateServerGUI() {
        int total_games = ((ThreadPoolExecutor) threadPoolGame).getActiveCount();
        this.waiting_queue_lock.lock();
        String[] waiting_queue = new String[this.waiting_queue.size()];
        for (int i = 0; i < this.waiting_queue.size() && i < 5; i++) {
            waiting_queue[i] = this.waiting_queue.get(i).getUsername();
        }
        serverGUI.setQueue(String.valueOf(this.waiting_queue.size()), waiting_queue);
        this.waiting_queue_lock.unlock();
        serverGUI.setGames(String.valueOf(total_games));

        this.database_lock.lock();
        serverGUI.setLeaderboard(this.database.getLeaderboard(3));
        this.database_lock.unlock();
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