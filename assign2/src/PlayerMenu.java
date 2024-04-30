import javax.swing.*;
import java.awt.*;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

public class PlayerMenu {

    private final JFrame f;
    private final JLabel timeLabel = new JLabel();
    private int currentTime = 0;
    private int currentRoundTime = 0;
    JLabel roundLabel = new JLabel("");
    JLabel questionLabel = new JLabel("Question");
    JLabel roundTimeLabel = new JLabel();

    private int timeout;

    //Timer that fires every 1 second
    private final Timer timer = new Timer(1000, e -> {
        currentTime++;
        timeLabel.setText("Time: " + currentTime + "s");
        currentRoundTime++;
        roundTimeLabel.setText("Time: " + (int) (timeout * 0.001 - currentRoundTime) + "s");
    });

    String[] options = {"Option 1", "Option 2", "Option 3", "Option 4"};

    // Colors for the GUI
    Color bg_color = new Color(10, 4, 41);
    Color card_color = new Color(28, 21, 61);

    public PlayerMenu(int timeoutTime) {

        f = new JFrame("Pop Quiz Game");
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        f.setSize(600, 400);
        f.getContentPane().setBackground(bg_color);
        f.setLayout(null);
        f.setVisible(true);

        roundLabel.setForeground(Color.WHITE);
        roundLabel.setBounds(50, 50, 100, 30);
        questionLabel.setForeground(Color.WHITE);
        questionLabel.setBounds(50, 100, 300, 30);
        roundTimeLabel.setForeground(Color.WHITE);
        roundTimeLabel.setBounds(450, 50, 100, 30);
        timeLabel.setForeground(Color.WHITE);
        timeLabel.setBounds(450, 50, 100, 30);

        timeout = timeoutTime;

        timer.start();
    }

    /*
     * Clear the GUI.
     */
    private void clearInterface() {
        f.getContentPane().removeAll();
        paintInterface();
    }

    /*
     * Paint the GUI.
     */
    private void paintInterface() {
        f.revalidate();
        f.repaint();
    }

    /*
     * Display the main menu in the GUI.
     * @return The option chosen by the player
     */
    public String mainMenu() {
        clearInterface();

        // Game Title
        JLabel title = new JLabel("Pop Quiz");
        title.setBounds(125, 25, 200, 50);
        title.setFont(new Font("Arial", Font.BOLD, 32));
        title.setForeground(Color.WHITE);
        f.add(title);

        String [] buttons = {"Login", "Register", "Restore Connection", "Quit"};
        final String[] result = new String[1];
        CountDownLatch latch = new CountDownLatch(1);

        for (int i = 0; i < 4; i++) {
            JButton b = new JButton(buttons[i]);
            b.setBounds(100,100+50*i,200, 40);
            int finalI = i;
            b.addActionListener(e -> {
                result[0] = Integer.toString(finalI + 1);
                latch.countDown();
            });
            f.add(b);
        }

        paintInterface();

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return result[0];
    }

    /*
     * Display the login and register fields in the GUI.
     * @param invalidCredentials Whether the credentials are invalid
     * @param takenUsername Whether the username is already taken
     * @return The username and password input by the player
     */
    public String[] loginAndRegister(boolean invalidCredentials, boolean takenUsername) {
        clearInterface();

        JLabel usernameLabel = new JLabel("Username:");
        usernameLabel.setForeground(Color.WHITE);
        JTextField usernameField = new JTextField(10);
        JLabel passwordLabel = new JLabel("Password:");
        passwordLabel.setForeground(Color.WHITE);
        JPasswordField passwordField = new JPasswordField(10);
        JButton submitButton = new JButton("Submit");
        final String[] result = new String[3];
        CountDownLatch latch = new CountDownLatch(1);

        usernameLabel.setBounds(75, 50, 100, 30);
        usernameField.setBounds(175, 50, 150, 30);
        passwordLabel.setBounds(75, 100, 100, 30);
        passwordField.setBounds(175, 100, 150, 30);
        submitButton.setBounds(200, 150, 100, 30);

        submitButton.addActionListener(e -> {
            result[0] = usernameField.getText();
            result[1] = new String(passwordField.getPassword());
            result[2] = "";
            latch.countDown();
        });

        f.add(usernameLabel);
        f.add(usernameField);
        f.add(passwordLabel);
        f.add(passwordField);
        f.add(submitButton);

        JButton backButton = new JButton("Back");
        backButton.setBounds(100, 150, 100, 30);
        f.add(backButton);
        backButton.addActionListener(e -> {
            result[0] = "BACK";
            result[1] = "BACK";
            result[2] = "BACK";
            latch.countDown();
        });

        if (invalidCredentials) {
            JLabel errorLabel = new JLabel("Invalid username or password");
            errorLabel.setBounds(50, 200, 200, 30);
            errorLabel.setForeground(Color.RED);
            f.add(errorLabel);
        }
        else if(takenUsername) {
            JLabel errorLabel = new JLabel("Username already taken");
            errorLabel.setBounds(50, 200, 200, 30);
            errorLabel.setForeground(Color.RED);
            f.add(errorLabel);
        }

        paintInterface();

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return result;
    }

    /*
     * Display the token file name input field in the GUI.
     * @param invalidToken Whether the token is invalid
     * @return The token file name input by the player
     */
    public String[] restore(boolean invalidToken) {
        clearInterface();

        JLabel tokenLabel = new JLabel("Token file name:");
        tokenLabel.setForeground(Color.WHITE);
        JTextField tokenField = new JTextField(10);
        JButton submitButton = new JButton("Submit");

        final String[] result = new String[2];
        CountDownLatch latch = new CountDownLatch(1);

        tokenLabel.setBounds(100, 65, 150, 30);
        tokenField.setBounds(125, 100, 150, 30);
        submitButton.setBounds(200, 150, 100, 30);

        submitButton.addActionListener(e -> {
            result[0] = tokenField.getText();
            result[1] = "";
            latch.countDown();
        });

        f.add(tokenLabel);
        f.add(tokenField);
        f.add(submitButton);

        JButton backButton = new JButton("Back");
        backButton.setBounds(100, 150, 100, 30);
        f.add(backButton);
        backButton.addActionListener(e -> {
            result[0] = "";
            result[1] = "BACK";
            latch.countDown();
        });

        if (invalidToken) {
            JLabel errorLabel = new JLabel("Invalid token");
            errorLabel.setBounds(25, 100, 100, 30);
            errorLabel.setForeground(Color.RED);
            f.add(errorLabel);
        }

        paintInterface();

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return result;
    }

    /*
     * Display the waiting queue in the GUI.
     * @param serverMessages The messages received from the server
     */
    public void queue(String serverMessage) {

        clearInterface();

        currentTime = 0;

        timeLabel.setText("Time: 0s");
        JLabel queueLabel = new JLabel("Waiting for opponent...");
        queueLabel.setForeground(Color.WHITE);
        JLabel serverMessageLabel = new JLabel(serverMessage);
        serverMessageLabel.setForeground(Color.WHITE);

        queueLabel.setBounds(150, 50, 200, 30);
        serverMessageLabel.setBounds(50, 150, 400, 30);

        f.add(queueLabel);
        f.add(timeLabel);
        f.add(serverMessageLabel);

        paintInterface();
    }

    /*
     * Display the question and options to the player.
     * The player has 15 seconds to answer the question.
     * If the player doesn't answer in time, the answer is considered wrong.
     * @return The answer chosen by the player
     */
    public String turn() {
        clearInterface();

        CountDownLatch latch = new CountDownLatch(1);

        // Create a timer that fires once after 30 seconds
        Timer timer = new Timer(timeout, e -> {
            latch.countDown();
        });

        timer.setRepeats(false); // Make sure the timer only fires once

        // Start the timer
        timer.start();

        // Reset Countdown timer
        currentRoundTime = 0;

        f.add(roundLabel);
        f.add(questionLabel);
        f.add(roundTimeLabel);

        String[] answer = new String[1];
        answer[0] = "T"; // Default value --> (T)imeout

        // create labels for options
        for (int i = 0; i < options.length; i++) {
            JButton optionButton = new JButton(options[i]);
            optionButton.setBounds(50, 150 + 50 * i, 250, 30);
            int finalI = i;
            optionButton.addActionListener(e -> {
                // return the answer A - D
                answer[0] = Character.toString((char) (finalI + 65));
                optionButton.setEnabled(false);
                optionButton.setBackground(Color.GREEN);
                latch.countDown();
            });
            f.add(optionButton);
        }

        paintInterface();

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return answer[0];
    }

    /*
     * Update the question in the GUI.
     * @param serverMessages The messages received from the server
     */
    public void updateQuestion(String[] serverMessages) {
        System.out.println("Updating question...");
        System.out.println(Arrays.toString(serverMessages));
        updateGameValues(serverMessages[1], serverMessages[2],
                serverMessages[3]
                        .substring(8, serverMessages[3].length() - 1)
                        .replace("[", "")
                        .replace("]", "")
                        .replace(" ", "")
                        .split(",")
        );
    }

    /*
     * Update the game values in the GUI.
     * @param round The round of the game
     * @param question The question to display
     * @param options The options to display
     */
    private void updateGameValues(String round, String question, String[] options) {
        roundLabel.setText(round);
        questionLabel.setText(question);
        this.options = options;
    }

    /*
     * Update the score in the GUI.
     * @param serverMessages The messages received from the server
     */
    public void updateScore(String[] serverMessages) {
        System.out.println("Updating score...");
        System.out.println(Arrays.toString(serverMessages));
        //updateScoreValues(serverMessages[1]);
    }


    public void info() {
        clearInterface();
        f.add(roundLabel);
        paintInterface();
    }

    /*
     * Display the winner of the game and ask the player if they want to play again.
     * @param serverMessages The messages received from the server
     */
    public String gameOver(String serverMessage) {
        clearInterface();

        System.out.println("Game over " + serverMessage);

        JLabel gameOverLabel = new JLabel("Game over!");
        gameOverLabel.setForeground(Color.WHITE);
        JLabel winnerLabel = new JLabel(serverMessage);
        winnerLabel.setForeground(Color.WHITE);
        JButton playAgainButton = new JButton("Play again");
        JButton quitButton = new JButton("Quit");

        final String[] result = new String[1];

        CountDownLatch latch = new CountDownLatch(1);

        gameOverLabel.setBounds(150, 50, 200, 30);
        winnerLabel.setBounds(150, 100, 200, 30);
        playAgainButton.setBounds(150, 150, 100, 30);
        quitButton.setBounds(150, 200, 100, 30);

        // Create a timer that fires once after 30 seconds
        Timer timer = new Timer(timeout, e -> {
            result[0] = "N";
            latch.countDown();
        });

        timer.setRepeats(false); // Make sure the timer only fires once

        // Start the timer
        timer.start();

        playAgainButton.addActionListener(e -> {
            result[0] = "Y";
            playAgainButton.setEnabled(false);
            quitButton.setEnabled(false);
            playAgainButton.setBackground(Color.GREEN);
            latch.countDown();
        });

        quitButton.addActionListener(e -> {
            result[0] = "N";
            playAgainButton.setEnabled(false);
            quitButton.setEnabled(false);
            quitButton.setBackground(Color.GREEN);
            latch.countDown();
        });

        f.add(gameOverLabel);
        f.add(winnerLabel);
        f.add(playAgainButton);
        f.add(quitButton);

        paintInterface();

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return result[0];
    }

    /*
     * Close the GUI.
     */
    public void close() {
        timer.stop();
        f.dispose();
    }
}