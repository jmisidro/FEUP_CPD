import java.io.*;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.*;

public class Connection {

    // Connection
    private final int port;                                 // The port number
    private final String host;                              // The host name or IP address
    private SocketChannel socket;                           // A SocketChannel for the connection

    // Player
    private PlayerMenu playerMenu;                            // A GUI to display messages
    private int authenticationOption = 0;

    // Constants
    private final String TOKEN_PATH = "player/";     // A path to a directory containing tokens
    private static final String DEFAULT_HOST = "localhost"; // A default host to use if none is provided
    private final long TIMEOUT = 30000;                     // Timeout to avoid slow clients in milliseconds


    public Connection(int port, String host) {
        this.port = port;
        this.host = host;
    }

    /*
     * Start the connection
     */
    public void start() throws IOException {
        this.socket = SocketChannel.open();             // Open a new SocketChannel
        this.socket.connect(new InetSocketAddress(this.host, this.port)); // Connect to the specified host and port
    }

    /*
     * Stop the connection
     */
    public void stop() throws IOException {
        this.socket.close(); // Close the SocketChannel
    }

    /*
     * Print the usage for the program
     */
    private static void printUsage() {
        System.out.println("usage: java Connection <PORT> [HOST]");
    }

    /*
     * Send a message to a SocketChannel
     * @param socket The SocketChannel to send the message to
     * @param message The message to send
     */
    public static void send(SocketChannel socket, String message) throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(1024);      // Create a ByteBuffer with a capacity of 1024 bytes
        buffer.clear();                                     // Clear the buffer
        buffer.put(message.getBytes());                     // Put the message into the buffer
        buffer.flip();                                      // Flip the buffer to prepare it for writing
        while (buffer.hasRemaining()) {                     // Write the buffer to the socket
            socket.write(buffer);
        }
    }


    /*
     * Receive a message from a SocketChannel
     * @param socket The SocketChannel to receive the message from
     * @return The message received from the SocketChannel
     *
     * Receive a message from a SocketChannel
     * @param socket The SocketChannel to receive the message from
     * - OPT: Option request
     * - USR: Data request: username or password
     * - TKN: Token request: session token value
     * - NACK: Handle an error in authentication
     * - AUTH: Authentication success. Receive session token value
     * - END: End of the connection
     */
    public static String receive(SocketChannel socket) throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(1024);          // Create a ByteBuffer with a capacity of 1024 bytes
        int bytesRead = socket.read(buffer);                    // Read from the socket into the buffer
        return new String(buffer.array(), 0, bytesRead); // Convert the bytes in the buffer to a String and return it
    }

    /*
     * Read a token from a file
     * @param filename The name of the file to read the token from
     * @return The token read from the file
     */
    public String readToken(String filename) {

        if (filename == null || filename.isEmpty()) {
            return null;
        }

        File file = new File(this.TOKEN_PATH + filename);

        // Check if the file exists
        if (!file.exists()) {
            System.out.println("File: "+ filename  + " does not exist");
            return null;
        }

        // Read the file content
        StringBuilder fileContent = new StringBuilder();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                fileContent.append(line);
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return fileContent.toString(); // Return the file content as a String
    }

    /*
     * Write a token to a file
     * @param filename The name of the file to write the token to
     * @param content The content to write to the file
     */
    public void writeToken(String filename, String content) {

        try {
            // Create the file if it doesn't exist
            File file = new File(this.TOKEN_PATH + filename);
            if (!file.exists()) {
                file.createNewFile();
            }

            // Write the content to the file
            BufferedWriter writer = new BufferedWriter(new FileWriter(file.getAbsoluteFile()));
            writer.write(content);
            writer.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*
     * Handle the authentication process with the server
     * @return True if the connection is authenticated, false otherwise
     */
    public boolean handleAuthentication() throws Exception {

        String[] serverAnswer;
        String requestType;
        boolean takenUsername = false;
        boolean invalidCredentials = false;
        boolean invalidToken = false;

        do {
            serverAnswer = Connection.receive(socket).split("\n");
            requestType = serverAnswer[0].toUpperCase();

            switch (requestType) {
                case "OPT" -> // Menu option request
                        Connection.send(socket, mainMenuMenu());
                case "USR" -> { // Data request: username or password (or BACK)
                    String[] credentials;

                    do {
                        credentials = this.loginAndRegister(invalidCredentials, takenUsername);
                    } while ((credentials[0].equals("") || credentials[1].equals("")) && !(credentials[2].equals("BACK")));


                    if (credentials[2].equals("BACK")) {
                        Connection.send(socket, credentials[2]);
                        takenUsername = false;
                        invalidCredentials = false;
                    }
                    else {
                        Connection.send(socket, credentials[0].toLowerCase());

                        serverAnswer = Connection.receive(socket).split("\n");
                        requestType = serverAnswer[0].toUpperCase(); // It is going to be PSW

                        if (!requestType.equals("END")) {
                            Connection.send(socket, credentials[1]);
                        }
                    }
                }
                case "TKN" -> { // Token request: session token value
                    String token;

                    do {
                        token = this.getTokenFromMenu(invalidToken);
                        invalidToken = token == null;
                    } while (token == null);

                    System.out.println("Token: " + token);
                    System.out.println("Sending token to server...");
                    Connection.send(socket, token);
                }
                case "NACK" -> { // Handle an error in authentication
                    System.out.println(serverAnswer[1]);

                    if(serverAnswer[1].equals("Username already in use")) {
                        takenUsername = true;
                    } else if(serverAnswer[1].equals("Wrong username or password")) {
                        invalidCredentials = true;
                    }
                    else if (serverAnswer[1].startsWith("Invalid session token")) {
                        invalidToken = true;
                    }

                    // Retry connection option chosen beforehand (if any)
                    // To do so, we first send a dummy "ACK" to the server,
                    // so it can send us the option request again
                    Connection.send(socket, "ACK");

                    if (authenticationOption > 0) {
                        // Retry connection option
                        Connection.send(socket,Integer.toString(authenticationOption));
                    }
                }
                case "AUTH" -> { // Authentication success. Session token received
                    System.out.println("Success! Session token was received.");
                    Connection.send(socket, "ACK");
                    this.writeToken(serverAnswer[1], serverAnswer[2]);
                }
                case "END" -> System.out.println(serverAnswer[1]); // End of the connection
                default -> System.out.println("Unknown server request type:" + requestType);
            }
        } while (!requestType.equals("AUTH") && !requestType.equals("END"));
        return requestType.equals("AUTH"); // Return true if the connection is authenticated
    }

    /*
     * Handle incoming server messages:
     * - QUEUE: Display the queue menu
     * - END: Close the connection
     * - INFO: Display the game information
     * - QUESTION: Update the question
     * - SCORE: Update the score
     * - TURN: Send the player's turn
     * - GAMEOVER: Display the game over message
     * - PING: Doesn't expect an answer back
     */
    public void handleServerMessages() throws Exception {
        Selector selector = Selector.open();
        socket.configureBlocking(false);
        socket.register(selector, SelectionKey.OP_READ);
        long lastTime = System.currentTimeMillis();

        while (true) {
            if (selector.select(TIMEOUT) == 0 && System.currentTimeMillis() - lastTime > TIMEOUT) {
                System.out.println("Server is not responding. Closing connection...");
                break;
            }
            lastTime = System.currentTimeMillis();
            selector.selectedKeys().clear();

            String[] serverAnswer = Connection.receive(socket).split("\n");
            String requestType = serverAnswer[0].toUpperCase();

            switch (requestType) {
                case "QUEUE": // Display the queue menu
                    Connection.send(socket, "ACK");
                    queueMenu(serverAnswer[1]);
                    break;
                case "END": // Close the connection
                    Connection.send(socket, "ACK");
                    return;
                case "INFO": // Display the game information
                case "QUESTION": // Update the question
                case "SCORE": // Update the score
                    gameMenu(serverAnswer, requestType);
                    Connection.send(socket, "ACK");
                    break;
                case "TURN": // Send the player's turn
                    Connection.send(socket, this.playerMenu.turn());
                    break;
                case "GAMEOVER": // Display the game over message
                    Connection.send(socket, this.playerMenu.gameOver(serverAnswer[1]));
                    break;
                case "PING": // Doesn't expect an answer back
                    break;
                default:
                    System.out.println("Unknown server request type");
            }
        }
        selector.close();
    }

    /*
     * Initialize the player menu
     */
    public void initMenu() {
        this.playerMenu = new PlayerMenu(15000);
    }

    /*
     * Display Main menu
     */
    public String mainMenuMenu() {
        authenticationOption = Integer.parseInt(this.playerMenu.mainMenu());
        return Integer.toString(authenticationOption);
    }

    /*
     * Display Login and register menu
     */
    public String[] loginAndRegister(boolean invalidCredentials, boolean takenUsername) {
        return this.playerMenu.loginAndRegister(invalidCredentials, takenUsername);
    }

    /*
     * Get a token from the menu
     */
    public String getTokenFromMenu(boolean invalidToken) {
        String[] result = this.playerMenu.restore(invalidToken);

        if(result[1].equals("BACK"))
            return result[1];
        else
            return readToken(result[0]);
    }

    /*
     * Display Queue menu
     */
    public void queueMenu(String serverMessage) {
        this.playerMenu.queue(serverMessage);
    }

    /*
     * Display Game menu
     */
    public void gameMenu(String[] serverMessages, String requestType) {
        switch (requestType) {
            case "INFO" -> this.playerMenu.info(serverMessages);
            case "QUESTION" -> this.playerMenu.updateQuestion(serverMessages);
            case "SCORE" -> this.playerMenu.updateScore(serverMessages);
        }
    }

    /*
     * Close the player menu
     */
    public void closeMenu() {
        if (this.playerMenu != null) this.playerMenu.close();
    }

    public static void main(String[] args) {

        // Check if the number of arguments is correct
        if (args.length < 1) {
            Connection.printUsage();
            return;
        }
        Connection connection = null;

        try {
            // Parse the arguments
            int port = Integer.parseInt(args[0]);
            String host = args.length == 2 ? args[1] : Connection.DEFAULT_HOST;

            // Create a start new connection
            connection = new Connection(port, host);
            connection.start();
            connection.initMenu();

            // Authenticate
            if (connection.handleAuthentication())
                connection.handleServerMessages();
            else
                connection.stop();

            connection.closeMenu();

        } catch (UnknownHostException exception) {
            System.out.println("Host not found: " + exception.getMessage());

        } catch (Exception exception) {
            System.out.println("I/O error: " + exception.getMessage());

        } finally {
            if (connection != null) {
                try {
                    connection.stop();
                    connection.closeMenu();
                } catch (IOException e) {
                    System.err.println("Error closing connection: " + e.getMessage());
                }
            }
        }
    }
}