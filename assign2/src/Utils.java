import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Utils {
    public static List<Question> parseQuestions() {
        List<Question> questions = new ArrayList<>();

        try {
            JSONParser parser = new JSONParser();
            JSONArray questionArray = (JSONArray) parser.parse(new FileReader("/server/questions.json"));

            for (Object questionObj : questionArray) {
                JSONObject questionJson = (JSONObject) questionObj;
                String questionText = (String) questionJson.get("question");
                JSONArray optionsArray = (JSONArray) questionJson.get("options");
                String answer = (String) questionJson.get("answer");

                List<String> options = new ArrayList<>();
                for (Object optionObj : optionsArray) {
                    String option = (String) optionObj;
                    options.add(option);
                }

                Question question = new Question(questionText, options, answer);
                questions.add(question);
            }
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }

        return questions;
    }
}

class Question {
    private String questionText;
    private List<String> options;
    private String answer;

    public Question(String questionText, List<String> options, String answer) {
        this.questionText = questionText;
        this.options = options;
        this.answer = answer;
    }

    public String getQuestionText() {
        return questionText;
    }

    public List<String> getOptions() {
        return options;
    }

    public String getAnswer() {
        return answer;
    }
}


