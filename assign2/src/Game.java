import java.io.IOException;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class Game implements Runnable {

    private final List<Player> players;
    private final Database database;
    private final ReentrantLock database_lock;
    private final List<Player> waiting_queue;
    private final ReentrantLock waiting_queue_lock;

    private static final int ROUNDS = 1;
    private final List<Question> questions;

    private int[] scores;

    public Game(List<Player> players, Database database, ReentrantLock database_lock,
                List<Player> waiting_queue,
                ReentrantLock waiting_queue_lock) {
        this.players = players;
        this.database = database;
        this.database_lock = database_lock;
        this.waiting_queue = waiting_queue;
        this.waiting_queue_lock = waiting_queue_lock;
        this.questions = Utils.getRandomQuestions(ROUNDS);
        this.scores = new int[this.players.size()];
        // initialize scores
        for (int i = 0 ; i < this.players.size() ; i++) {
            this.scores[i] = 0;
        }
    }

    /*
     * Start the game.
     * The game is made of at least 2 players.
     * The game is played in 4 rounds.
     * The winner is the player with the highest score.
     * If there's a tie, the players will divide between themselves the gained elo.
     */
    public void run() {
        try {
            System.out.println("Starting quiz with " + this.players.size() + " players");
            String winner = this.playGameRounds();
            System.out.println("Quiz finished. Winner: " + winner);
            this.handlePostGame(winner);
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
    private void handlePostGame(String winner) throws Exception {
        for (Player player : this.players) {
            Server.request(player.getSocket(), "GAMEOVER", winner);
            String response = Connection.receive(player.getSocket());

            if (response.equals("Y")) {
                addPlayerToQueue(player);
            } else {
                endConnection(player);
            }
        }
    }

    /*
     * End the connection with the player.
     * The player's session token is invalidated and the connection is closed.
     *
     * @param player The player to end the connection with
     */
    private void endConnection(Player player) throws Exception {
        Server.request(player.getSocket(), "END", "Connection closed");
        this.database_lock.lock();
        try {
            this.database.invalidateToken(player);
            this.database.backup();
        } finally {
            this.database_lock.unlock();
        }
        player.getSocket().close();
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
     * Wins the player that has the biggest score after N playGameRounds.
     * If there's a tie, the players will divide between themselves the gained elo.
     */
    private String playGameRounds() throws Exception {
        notifyPlayers("INFO", "Game Started", null);

        if(players.size() < 2) {
            notifyPlayers("END", "Not enough players to start the game", null);
            return "Not enough players to start the game";
        }

        for (int round = 0; round < ROUNDS; round++) {
            for (Player player : players) {
                printQuestion(player, round);
                notifyPlayers("INFO", "It's " + player.getUsername() + "'s turn", player);
                Server.request(player.getSocket(), "TURN", "Your turn to answer. Choose a letter between A and D.");
                String answer = Connection.receive(player.getSocket());
                System.out.println("Player " + player.getUsername() + " answered: " + answer + " in round " + round);
                if (answer.equals(questions.get(round).getAnswer())) {
                    scores[players.indexOf(player)]++;
                }
                printCurrentScores();
            }
        }

        return determineWinner();
    }

    /*
     * Determine the winner of the game.
     * The winner is the player with the highest score.
     * The winner's rank is updated in the database.
     */
    private String determineWinner() throws Exception {
        String winner = "";
        int winnerScore = 0;

        for (Player player : players) {
            player.incrementRank(scores[players.indexOf(player)]);
            updateDatabaseRank(player);
            if (scores[players.indexOf(player)] > winnerScore) {
                winner = player.getUsername() + " won with " + scores[players.indexOf(player)] + " points!";
                winnerScore = scores[players.indexOf(player)];
            }
        }

        return winner;
    }

    /*
     * Update the player's rank in the database.
     * @param player The player to update the rank
     */
    private void updateDatabaseRank(Player player) throws Exception {
        this.database_lock.lock();
        this.database.updateRank(player, this.scores[this.players.indexOf(player)]);
        this.database.backup();
        this.database_lock.unlock();
    }

    /*
     * Print the question to the player.
     * @param player The player that will receive the question
     * @param round The round of the game
     */
    private void printQuestion(Player player, int round) throws Exception {
        Question question = questions.get(round);
        String questionText = "Round: " + (round + 1) + "/" + ROUNDS + "\n" +
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
        this.players.stream()
                .filter(player -> excluded == null || !player.equals(excluded))
                .forEach(player -> sendPlayerMessage(player, messageType, message));
    }

    /*
     * Send a message to a player.
     * @param player The player to send the message to
     * @param messageType The type of the message
     * @param message The message to be sent
     */
    private void sendPlayerMessage(Player player, String messageType, String message) {
        try {
            Server.request(player.getSocket(), messageType, message);
            Connection.receive(player.getSocket());
        } catch (Exception exception) {
            System.out.println("Exception: " + exception.getMessage());
        }
    }


}