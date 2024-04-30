import java.io.*;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.*;

public class Connection {

    private final int port;                                 // The port number
    private final String host;                              // The host name or IP address
    private SocketChannel socket;                           // A SocketChannel for the connection
    private final String TOKEN_PATH = "assign2/src/client/";     // A path to a directory containing tokens
    private static final String DEFAULT_HOST = "localhost"; // A default host to use if none is provided
    private final long TIMEOUT = 30000;                     // Timeout to avoid slow clients in milliseconds
    private PlayerMenu playerMenu;                            // A GUI to display messages
    private int authenticationOption = 0;

    // Constructor
    public Connection(int port, String host) {
        this.port = port;
        this.host = host;
    }

    // Method to start the connection
    public void start() throws IOException {
        this.socket = SocketChannel.open();             // Open a new SocketChannel
        this.socket.connect(new InetSocketAddress(this.host, this.port)); // Connect to the specified host and port
    }

    // Method to stop the connection
    public void stop() throws IOException {
        this.socket.close(); // Close the SocketChannel
    }

    // Class usage
    private static void printUsage() {
        System.out.println("usage: java Connection <PORT> [HOST]");
    }

    // Static method to send a message through a SocketChannel
    public static void send(SocketChannel socket, String message) throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(1024);      // Create a ByteBuffer with a capacity of 1024 bytes
        buffer.clear();                                     // Clear the buffer
        buffer.put(message.getBytes());                     // Put the message into the buffer
        buffer.flip();                                      // Flip the buffer to prepare it for writing
        while (buffer.hasRemaining()) {                     // Write the buffer to the socket
            socket.write(buffer);
        }
    }

    // Static method to receive a message from a SocketChannel
    /*
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

    public String readToken(String filename) {

        if (filename == null || filename.isEmpty()) {
            return null;
        }

        File file = new File(this.TOKEN_PATH + filename);

        // Check if the file exists
        if (!file.exists()) {
            System.out.println("File - "+ filename  + " - does not exist");
            return null;
        }

        // Retrieve file content
        StringBuilder fileContent = new StringBuilder(); // Create StringBuilder to hold file content
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file)); // Create BufferedReader to read file
            String line;
            while ((line = reader.readLine()) != null) {    // Read each line of the file
                fileContent.append(line);
            }
            reader.close(); // Close the BufferedReader
        } catch (IOException e) {
            e.printStackTrace();
        }

        return fileContent.toString(); // Return the file content as a String
    }

    public void writeToken(String filename, String content) {

        try {
            // Check if the file exists
            File file = new File(this.TOKEN_PATH + filename);
            if (!file.exists()) {
                file.createNewFile();
            }

            // Update file content
            BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(file.getAbsoluteFile()));
            bufferedWriter.write(content); // Write the content to the file
            bufferedWriter.close(); // Close the BufferedWriter

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean authenticate() throws Exception {

        String[] serverAnswer;
        String requestType;
        boolean takenUsername = false;
        boolean invalidCredentials = false;
        boolean invalidToken = false;

        do {
            serverAnswer = Connection.receive(socket).split("\n");
            requestType = serverAnswer[0].toUpperCase();

            switch (requestType) {
                case "OPT" -> { // Option request
                    String menu = String.join("\n", Arrays.copyOfRange(serverAnswer, 1, serverAnswer.length));
                    System.out.println(menu);
                    Connection.send(socket, mainMenuMenu());
                }
                case "USR" -> { // Data request: username or password

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
                    System.out.println(serverAnswer[1]);
                    System.out.println("Token file name: ");
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


                    // If we receive an error authenticating, we will try again the same option chosen before, unless it wasn't chosen, yet
                    // We first need to send the dummy "ACK" to the server, so it can send us the option request again
                    Connection.send(socket, "ACK");

                    if (authenticationOption > 0) {
                        serverAnswer = Connection.receive(socket).split("\n");
                        requestType = serverAnswer[0].toUpperCase();

                        // Retry connection option
                        Connection.send(socket,Integer.toString(authenticationOption));
                    }
                }
                case "AUTH" -> { // Authentication success. Receive session token value
                    System.out.println("Success. Session token was received.");
                    Connection.send(socket, "ACK");
                    this.writeToken(serverAnswer[1], serverAnswer[2]);
                }
                case "END" -> System.out.println(serverAnswer[1]); // If the server brokes the connection
                default -> System.out.println("Unknown server request type :" + requestType);
            }
        } while (!requestType.equals("AUTH") && !requestType.equals("END"));
        return requestType.equals("AUTH"); // Authentication success?
    }

    public void listening() throws Exception {

        String[] serverAnswer;
        String requestType = "";
        long lastTime = System.currentTimeMillis();
        long currentTime;
        Selector selector = Selector.open();
        socket.configureBlocking(false);
        socket.register(selector, SelectionKey.OP_READ);

        do {

            int readyChannels = selector.select(TIMEOUT);
            if (readyChannels == 0) {
                currentTime = System.currentTimeMillis() - lastTime;
                if(currentTime > TIMEOUT) {
                    System.out.println("Server is not responding. Closing connection...");
                    break;
                }
                continue;
            } else {
                lastTime = System.currentTimeMillis();
                selector.selectedKeys().clear();
            }

            serverAnswer = Connection.receive(socket).split("\n");
            requestType = serverAnswer[0].toUpperCase();
            System.out.println("REQUEST TYPE: " + Arrays.toString(serverAnswer));

            switch (requestType) {
                case "QUEUE" -> {
                    Connection.send(socket, "ACK");
                    queueMenu(serverAnswer[1]);
                }
                case "END" -> {
                    Connection.send(socket, "ACK");
                }
                case "INFO", "QUESTION", "SCORE" -> { // Player turn. Let's send something to server.
                    gameMenu(serverAnswer, requestType);
                    Connection.send(socket, "ACK");
                }
                case "TURN" -> {
                    Connection.send(socket, this.playerMenu.turn());
                }
                case "GAMEOVER" -> {
                    Connection.send(socket, this.playerMenu.gameOver(serverAnswer[1]));
                }
                case "PING" -> {
                    ; // Doesn't expect an answer back
                }
                default -> System.out.println("Unknown server request type");
            }

        } while (!requestType.equals("END"));
        selector.close();

    }

    public void initMenu() {
        this.playerMenu = new PlayerMenu(15000);
    }

    public String mainMenuMenu() {
        authenticationOption = Integer.parseInt(this.playerMenu.mainMenu());
        return Integer.toString(authenticationOption);
    }

    public String[] loginAndRegister(boolean invalidCredentials, boolean takenUsername) {
        return this.playerMenu.loginAndRegister(invalidCredentials, takenUsername);
    }

    public String getTokenFromMenu(boolean invalidToken) {
        String[] result = this.playerMenu.restore(invalidToken);

        if(result[1].equals("BACK"))
            return result[1];
        else
            return readToken(result[0]);
    }

    public void queueMenu(String serverMessage) {
        this.playerMenu.queue(serverMessage);
    }

    public void gameMenu(String[] serverMessages, String requestType) {
        switch (requestType) {
            case "INFO" -> this.playerMenu.info(serverMessages);
            case "QUESTION" -> this.playerMenu.updateQuestion(serverMessages);
            case "SCORE" -> this.playerMenu.updateScore(serverMessages);
        }
    }

    public void closeMenu() {
        if (this.playerMenu != null) this.playerMenu.close();
    }

    public static void main(String[] args) {

        // Check if there are enough arguments
        if (args.length < 1) {
            Connection.printUsage();
            return;
        }
        Connection connection = null;

        // Parse port and host arguments and create a Connection object
        try {

            int port = Integer.parseInt(args[0]);
            String host = args.length == 2 ? args[1] : Connection.DEFAULT_HOST;
            connection = new Connection(port, host);
            connection.start();
            connection.initMenu();

            // Start the connection and authenticate
            if (connection.authenticate())
                connection.listening();
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