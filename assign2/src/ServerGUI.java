import javax.swing.*;
import java.awt.*;

public class ServerGUI {

    private final JFrame f;
    private final JLabel timeLabel = new JLabel();
    private final JLabel numberOfGames = new JLabel();
    private final JLabel leaderboard = new JLabel();
    private final JLabel queuePlayers = new JLabel();
    private int currentTime = 0;

    // Colors for the GUI
    Color bg_color = new Color(10, 4, 41);
    Color card_color = new Color(28, 21, 61);

    // Timer that fires every 1 second
    private final Timer timer = new Timer(1000, e -> {
        currentTime++;
        timeLabel.setText("Time: " + currentTime + "s");
    });

    public ServerGUI() {

        f = new JFrame("Pop Quiz Server");
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setSize(600, 400);
        f.getContentPane().setBackground(bg_color);
        f.setLayout(null);
        f.setVisible(true);

        // Game Title
        JLabel title = new JLabel("Pop Quiz");
        title.setBounds(200, 50, 200, 50);
        title.setFont(new Font("Arial", Font.BOLD, 42));
        title.setForeground(Color.WHITE);
        f.add(title);

        // Information Panel
        JPanel infoPanel = new JPanel();
        infoPanel.setBounds(25, 150, 250, 200);
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setBackground(card_color);
        infoPanel.add(Box.createRigidArea(new Dimension(10, 10)));

        // Time Label
        timeLabel.setText("Time: " + currentTime + "s");
        timeLabel.setForeground(Color.WHITE);

        // Number of Games Label
        numberOfGames.setForeground(Color.WHITE);

        // Queue Players Label
        queuePlayers.setForeground(Color.WHITE);

        infoPanel.add(timeLabel);
        infoPanel.add(numberOfGames);
        infoPanel.add(queuePlayers);
        f.add(infoPanel);

        // Leaderboard Panel
        JPanel leaderboardPanel = new JPanel();
        leaderboardPanel.setBounds(325, 150, 250, 200);
        leaderboardPanel.setLayout(new BoxLayout(leaderboardPanel, BoxLayout.Y_AXIS));
        leaderboardPanel.setBackground(card_color);
        leaderboardPanel.add(Box.createRigidArea(new Dimension(10, 10)));

        // Leaderboard Label
        leaderboard.setForeground(Color.WHITE);

        leaderboardPanel.add(leaderboard);
        f.add(leaderboardPanel);

        this.paintInterface();
        timer.start();
    }

    private void paintInterface() {
        f.revalidate();
        f.repaint();
    }

    public void setQueue(String queueSize, String[] players) {
        StringBuilder playersString = new StringBuilder("<html>Players in queue: " + queueSize + "<br>");
        for (String s : players) {
            playersString.append(s).append("<br>");
        }
        playersString.append("</html>");
        queuePlayers.setText(playersString.toString());
    }

    public void setGames(String games) {
        numberOfGames.setText("Number of active games: "+ games);
    }

    public void setLeaderboard(String[] leaderboard) {
        StringBuilder leaderboardString = new StringBuilder("<html>Leaderboard:<br>");
        for (String s : leaderboard) {
            leaderboardString.append(s).append("<br>");
        }
        leaderboardString.append("</html>");
        this.leaderboard.setText(leaderboardString.toString());
    }
}