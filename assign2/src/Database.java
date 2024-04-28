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
    public Player login(String username, String password, String token, SocketChannel socket) {
        // Get the users from the database
        JSONArray savedUsers = (JSONArray) this.database.get("database");
        for (Object obj : savedUsers) {
            JSONObject user = (JSONObject) obj;
            String savedUsername = (String) user.get("username");
            String savedPassword = (String) user.get("password");

            // If a match is found, update the user's token and return a new Player object
            if (savedUsername.equals(username) && BCrypt.checkpw(password, savedPassword)) {
                user.put("token", token);
                Long rank = ((Number) user.get("rank")).longValue();
                return new Player(username, savedPassword, token, rank, socket);
            }
        }
        // If no match is found, return null
        return null;
    }

    // Register a new user to the Database
    public Player register(String username, String password, String token, SocketChannel socket) {
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

        // Return a new Player object for the new user
        return new Player(username, passwordHash, token, 0L, socket);
    }

    // Restore a user's session based on their token
    public Player restore(String token, SocketChannel socket) {
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
                return new Player(username, password, token, rank, socket);
            }
        }
        // If no matching token is found, return null
        return null;
    }

    // Update the user's rank in the Database
    public void updateRank(Player player, int value) {
        // Get the users from the database
        JSONArray savedUsers = (JSONArray) this.database.get("database");
        for (Object obj : savedUsers) {
            JSONObject user = (JSONObject) obj;
            String username = (String) user.get("username");
            if (username.equals(player.getUsername())) {

                // If there is a match, update the user's rank
                Long rank = ((Number) user.get("rank")).longValue() + value;
                user.put("rank", rank);
                return;
            }
        }
    }

    // Invalidates the token for the given user
    public void invalidateToken(Player player) {
        // Get the users from the database
        JSONArray savedUsers = (JSONArray) this.database.get("database");
        for (Object obj : savedUsers) {
            JSONObject user = (JSONObject) obj;
            String username = (String) user.get("username");

            // If the username matches the username of the player whose token needs to be invalidated
            if (username.equals(player.getUsername())) {
                user.put("token", "");
                return;
            }
        }
    }

    // Resets all tokens in the database
    public void resetTokens() {
        // Get the users from the database
        JSONArray savedUsers = (JSONArray) this.database.get("database");
        for (Object obj : savedUsers) {
            JSONObject user = (JSONObject) obj;
            user.put("token", "");
        }
    }

    // Returns the leaderboard
    public String[] getLeaderboard(int n) {
        String[] leaderboard = new String[n];
        // Get the users from the database
        JSONArray savedUsers = (JSONArray) this.database.get("database");
        List<JSONObject> userList = new ArrayList<>();
        for (Object obj : savedUsers) {
            JSONObject user = (JSONObject) obj;
            userList.add(user);
        }
        userList.sort((a, b) -> Long.compare(((Number) b.get("rank")).longValue(), ((Number) a.get("rank")).longValue()));
        for (int i = 0; i < n && i < userList.size(); i++) {
            JSONObject user = userList.get(i);
            String username = (String) user.get("username");
            long rank = ((Number) user.get("rank")).longValue();
            leaderboard[i] = username + " - " + rank;
        }
        return leaderboard;
    }

}