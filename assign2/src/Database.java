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
}