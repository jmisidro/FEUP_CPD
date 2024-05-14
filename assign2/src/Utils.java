import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Utils {

    private static final String FILE_PATH = "server/";

    /*
     * Parse the questions from the questions.json file inside FILE_PATH
     * @return: a list of questions
     */
    public static List<Question> parseQuestions() {
        List<Question> questions = new ArrayList<>();

        try {
            JSONParser parser = new JSONParser();
            JSONObject rootObject = (JSONObject) parser.parse(new FileReader(FILE_PATH + "questions.json"));
            JSONArray questionArray = (JSONArray) rootObject.get("questions");

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

    /*
     * Get n random questions from the list of questions
     * @param n: the number of questions to get
     * @return: a list of n random questions
     */
    public static List<Question> getRandomQuestions(int n) {
        List<Question> questions = parseQuestions();
        List<Question> randomQuestions = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int randomIndex = (int) (Math.random() * questions.size());
            randomQuestions.add(questions.get(randomIndex));
            questions.remove(randomIndex);
        }
        return randomQuestions;
    }

    public static void main(String[] args) {
        List<Question> questions = Utils.parseQuestions();
        for (Question question : questions) {
            System.out.println(question.getQuestionText());
            System.out.println("Options: " + question.getOptions());
            System.out.println("Answer: " + question.getAnswer());
            System.out.println();
        }
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

