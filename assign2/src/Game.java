import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class Game implements Runnable {

    private final List<Player> players;
    private final Database database;
    private final ReentrantLock database_lock;
    private final List<Player> waiting_queue;
    private final ReentrantLock waiting_queue_lock;
    private final int ROUNDS = 4;

    private List<Question> questions;

    private int[] scores;

    public Game(List<Player> players, Database database, ReentrantLock database_lock,
                List<Player> waiting_queue,
                ReentrantLock waiting_queue_lock) {
        this.players = players;
        this.database = database;
        this.database_lock = database_lock;
        this.waiting_queue = waiting_queue;
        this.waiting_queue_lock = waiting_queue_lock;
        this.questions = Utils.parseQuestions();
        this.scores = new int[this.players.size()];
        // initialize scores
        for (int i = 0 ; i < this.players.size() ; i++) {
            this.scores[i] = 0;
        }
    }

    public void run() {
        try {
            System.out.println("Starting quiz with " + this.players.size() + " players");
            String winner = this.rounds();
            System.out.println("Quiz finished. Winner: " + winner);
            this.askPlayAgain(winner);
        } catch (Exception exception) {
            System.out.println("Exception occurred during game. Connection closed. : " + exception.getMessage());
            this.notifyPlayers("END", "Exception occurred during game. Connection closed.", null);
        }
    }

    /*
     * Ask the players if they want to play again.
     * If they want to play again, they are placed in the waiting queue.
     * If they don't want to play again, their session token is invalidated and the connection is closed.
     *
     * @param winner The winner of the game
     */
    private void askPlayAgain(String winner) throws Exception {
        for (Player player : this.players) {

            Server.request(player.getSocket(), "GAMEOVER", winner);
            String answer = Connection.receive(player.getSocket());

            // Wants to play more: is placed in the waiting queue
            if (answer.equals("Y")) {
                addPlayerToQueue(player);

                // Don't want to play again: session token is invalidated and connection is closed
            } else {
                Server.request(player.getSocket(), "END", "Connection closed");
                this.database_lock.lock();
                this.database.invalidateToken(player);
                this.database.backup();
                this.database_lock.unlock();
                player.getSocket().close();
            }
        }
    }

    /*
     * Add a player to the waiting queue.
     * If the player is already in the queue, their socket is updated with the new one.
     * If the player is not in the queue, they are added to the end of the queue.
     *
     * @param player The player to be added to the queue
     */
    private void addPlayerToQueue(Player player) {
        this.waiting_queue_lock.lock();
        try {
            Player existingPlayer = this.waiting_queue.stream()
                    .filter(p -> p.equals(player))
                    .findFirst()
                    .orElse(null);

            if (existingPlayer != null) { // player already exists in the queue
                existingPlayer.setSocket(player.getSocket());
                System.out.println("Player " + player.getUsername() + " reconnected. Queue size: " + this.waiting_queue.size());
            } else { // else, player is added to the queue
                this.waiting_queue.add(player);
                System.out.println("Player " + player.getUsername() + " is now in the waiting queue. Queue size: " + this.waiting_queue.size());
            }

            Server.request(player.getSocket(), "QUEUE", "You joined the waiting queue with ranking of  " + player.getRank() + " points.");
            Connection.receive(player.getSocket());
        } catch (Exception exception) {
            System.out.println("Error while adding player to the waiting queue. Info: " + exception.getMessage());
        } finally {
            this.waiting_queue_lock.unlock();
        }
    }

    /*
     * The game will ask each user to throw 2 dices.
     * The game is made of at least 2 players.
     * Each user will throw the dices simultaneously.
     * The server will give the result of the throw to each user.
     * Wins the player that has the biggest score after N rounds.
     * If there's a tie, the players will divide between themselves the gained elo.
     */
    private String rounds() throws Exception {

        // Game started
        this.notifyPlayers("INFO", "Game Started", null);

        if(this.players.size() < 2) {
            this.notifyPlayers("END", "Not enough players to start the game", null);
            return "Not enough players to start the game";
        }

        // Game rounds loop
        String answer;
        for (int round = 0 ; round < this.ROUNDS ; round++) {
            for (Player player : this.players) {
                printCurrentScores();
                printQuestion(player, round);
                this.notifyPlayers("INFO", "It's " + player.getUsername() + " turn to answer the question", player);
                Server.request(player.getSocket(), "TURN", "Your turn to answer the question. Choose a letter between A and D.");
                answer = Connection.receive(player.getSocket());
                System.out.println("Player " + player.getUsername() + " answered: " + answer + " in round " + round + " - " + this.questions.get(round).getAnswer());
                // update scores
                if (answer.equals(this.questions.get(round).getAnswer())) {
                    this.scores[this.players.indexOf(player)] += 1;
                }
            }
        }

        String winner = "";
        int winnerScore = 0;

        // Sending results
        for (Player player : this.players) {
            this.notifyPlayers("INFO", "Player " + player.getUsername() + " has " + this.scores[this.players.indexOf(player)] + " points", null);

            // Update the player's rank
            player.incrementRank(this.scores[this.players.indexOf(player)]);

            // Update the database
            this.database_lock.lock();
            this.database.updateRank(player, this.scores[this.players.indexOf(player)]);
            this.database.backup();
            this.database_lock.unlock();

            // Check for the winner
            if (this.scores[this.players.indexOf(player)] > winnerScore) {
                winner = player.getUsername() + " won with " + this.scores[this.players.indexOf(player)] + " points!";
                winnerScore = this.scores[this.players.indexOf(player)];
            }
        }

        return winner;
    }

    /*
     * Print the question to the player.
     * @param player The player that will receive the question
     * @param round The round of the game
     */
    private void printQuestion(Player player, int round) throws Exception {
        Question question = this.questions.get(round);
        String questionText = "Round: " + (round + 1) + "\n" +
                "Question: " + question.getQuestionText() + "\n" +
                "Options: " + question.getOptions() + "\n";
        Server.request(player.getSocket(), "QUESTION", questionText);
        Connection.receive(player.getSocket());
    }

    /*
     * Print the current scores of the players in the game.
     */
    private void printCurrentScores() {
        StringBuilder results = new StringBuilder();
        for (Player player : this.players) {
            results.append(player.getUsername()).append(" Score: ").append(scores[this.players.indexOf(player)]).append("\n");
        }
        this.notifyPlayers("SCORE", results.toString(), null);
    }

    /*
     * Notify all players in the game with a message.
     * @param messageType The type of the message
     * @param message The message to be sent
     * @param excluded The player that will not receive the message
     */
    private void notifyPlayers(String messageType, String message, Player excluded) {
        try {
            for (Player player : this.players) {
                if (excluded != null && player.equals(excluded)) continue;
                Server.request(player.getSocket(), messageType, message);
                Connection.receive(player.getSocket());
            }
        } catch (Exception exception) {
            System.out.println("Exception: " + exception.getMessage());
        }
    }


}