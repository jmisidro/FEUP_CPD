import java.io.*;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.security.crypto.bcrypt.BCrypt;

class Database {

    private final File file;
    private final JSONObject database;

    public Database(String filename) throws IOException, ParseException {

        // File --> create if it doesn't exist
        this.file = new File(filename);
        if (!file.exists()) {
            createEmptyFile();
        }

        // File content --> read and parse JSON
        StringBuilder content = new StringBuilder();
        BufferedReader reader = new BufferedReader(new FileReader(file));
        String line;
        while ((line = reader.readLine()) != null) {
            content.append(line);
        }
        reader.close();

        // Database as a JSON Object
        this.database = (JSONObject) new JSONParser().parse(content.toString());
    }

    // Creates an empty file with an empty JSON object, if the file doesn't already exist
    private void createEmptyFile() throws IOException {
        JSONObject emptyObject = new JSONObject();
        emptyObject.put("database", new JSONArray());
        FileWriter writer = new FileWriter(this.file);
        writer.write(emptyObject.toJSONString());
        writer.close();
    }

    // Updates the database file with the current database object
    public void backup() throws IOException {
        FileWriter writer = new FileWriter(this.file);
        writer.write(this.database.toJSONString());
        writer.close();
    }

    // Authenticate a user with their username and password in the Database
    public Client login(String username, String password, String token, SocketChannel socket) {
        // Get the users from the database
        JSONArray savedUsers = (JSONArray) this.database.get("database");
        for (Object obj : savedUsers) {
            JSONObject user = (JSONObject) obj;
            String savedUsername = (String) user.get("username");
            String savedPassword = (String) user.get("password");

            // If a match is found, update the user's token and return a new Client object
            if (savedUsername.equals(username) && BCrypt.checkpw(password, savedPassword)) {
                user.put("token", token);
                Long rank = ((Number) user.get("rank")).longValue();
                return new Client(username, savedPassword, token, rank, socket);
            }
        }
        // If no match is found, return null
        return null;
    }

    // Register a new user to the Database
    public Client register(String username, String password, String token, SocketChannel socket) {
        // Get the users from the database
        JSONArray savedUsers = (JSONArray) this.database.get("database");
        for (Object obj : savedUsers) {
            JSONObject user = (JSONObject) obj;
            String savedUsername = (String) user.get("username");

            // If the username already exists, return null
            if (savedUsername.equals(username)) {
                return null;
            }
        }

        // If the username is not taken, create a new JSONObject for the new user
        JSONObject newClient = new JSONObject();
        String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt());
        newClient.put("username", username);
        newClient.put("password", passwordHash);
        newClient.put("token", token);
        newClient.put("rank", 0);

        // Add the new user to the array of users
        savedUsers.add(newClient);
        this.database.put("database", savedUsers);

        // Return a new Client object for the new user
        return new Client(username, passwordHash, token, 0L, socket);
    }

    // Restore a user's session based on their token
    public Client restore(String token, SocketChannel socket) {
        // Get the users from the database
        JSONArray savedUsers = (JSONArray) this.database.get("database");
        for (Object obj : savedUsers) {
            JSONObject user = (JSONObject) obj;
            String savedToken = (String) user.get("token");

            // Check if the stored token matches the provided token
            if (savedToken.equals(token)) {
                String username = (String) user.get("username");
                String password = (String) user.get("password");
                Long rank = ((Number) user.get("rank")).longValue();
                return new Client(username, password, token, rank, socket);
            }
        }
        // If no matching token is found, return null
        return null;
    }

}