import java.nio.channels.SocketChannel;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class Game implements Runnable {

    private final List<Client> players;
    private final Database database;
    private final ReentrantLock database_lock;
    private final List<Client> waiting_queue;
    private final ReentrantLock waiting_queue_lock;
    private final int ROUNDS = 4;

    private List<Question> questions;

    private int[] scores;

    public Game(List<Client> players, Database database, ReentrantLock database_lock,
                List<Client> waiting_queue,
                ReentrantLock waiting_queue_lock) {
        this.players = players;
        this.database = database;
        this.database_lock = database_lock;
        this.waiting_queue = waiting_queue;
        this.waiting_queue_lock = waiting_queue_lock;
        this.questions = Utils.parseQuestions();
        this.scores = new int[this.players.size()];
    }

    public void run() {
        try {
            System.out.println("Starting quiz with " + this.players.size() + " players");
            String winner = this.rounds();
            System.out.println("Quiz finished. Winner: " + winner);
            this.askPlayAgain(winner);
        } catch (Exception exception) {
            System.out.println("Exception ocurred during game. Connection closed. : " + exception.getMessage());
            this.notifyPlayers("END", "Exception ocurred during game. Connection closed.", null);
        }
    }

    /*
     * Get the sockets of the players in the game
     */
    private SocketChannel[] getPlayersSockets() {
        SocketChannel[] sockets = new SocketChannel[this.players.size()];
        for (int i = 0 ; i < this.players.size() ; i++) {
            sockets[i] = this.players.get(i).getSocket();
        }
        return sockets;
    }

    /*
     * Ask the players if they want to play again.
     * If they want to play again, they are placed in the waiting queue.
     * If they don't want to play again, their session token is invalidated and the connection is closed.
     * @param winner The winner of the game
     */
    private void askPlayAgain(String winner) throws Exception {
        for (Client player : this.players) {

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
    private void addPlayerToQueue(Client player) {
        this.waiting_queue_lock.lock();
        try {
            for (Client c : this.waiting_queue) {
                if (c.equals(player)) {
                    // If the client is already in the queue, their socket is updated with the new one
                    System.out.println("FOUND DUPLICATE IN QUEUE");
                    c.setSocket(player.getSocket());
                    System.out.println("Client " + player.getUsername() + " reconnected. Queue size: " + this.waiting_queue.size());
                    Server.request(player.getSocket(), "QUEUE", "You joined the waiting queue with a ranking of " + player.getRank() + " points.");
                    Connection.receive(player.getSocket());
                    this.waiting_queue_lock.unlock();
                    return;
                }
            }

            // If the client is not in the queue, add them to its end
            this.waiting_queue.add(player);
            Server.request(player.getSocket(), "QUEUE", "You joined the waiting queue with ranking of  " + player.getRank() + " points.");
            Connection.receive(player.getSocket());
            System.out.println("Client " + player.getUsername() + " is now in the waiting queue. Queue size: " + this.waiting_queue.size());
            this.waiting_queue_lock.unlock();

        } catch (Exception exception) {
            System.out.println("Error while adding player to the waiting queue. Info: " + exception.getMessage());
        }
    }

    /*
     * The game will ask each user to throw 2 dices.
     * The game is made of at least 2 players.
     * Each user will throw the dices simultaneously.
     * The server will give the result of the throw to each user.
     * Wins the player that has the biggest score after N rounds.
     * If there's a tie, the players will divide between themselfs the gained elo.
     */
    private String rounds() throws Exception {

        // Game started
        this.notifyPlayers("INFO", "Game Started", null);

        if(this.players.size() < 2) {
            this.notifyPlayers("END", "Not enough players to start the game", null);
            return "Not enough players to start the game";
        }

        // Game rounds loop
        String[] answers = new String[this.players.size()];
        for (int round = 0 ; round < this.ROUNDS ; round++) {
            for (Client player : this.players) {
                printCurrentScores(round);
                printQuestion(player, round);
                this.notifyPlayers("INFO", "It's " + player.getUsername() + " turn to answer the question", player);
                Server.request(player.getSocket(), "TURN", "Your turn to answer the question. Choose a letter between A and D.");
                answers[this.players.indexOf(player)] = Connection.receive(player.getSocket());
                System.out.println(" round " + round + " - " + answers[this.players.indexOf(player)]  + ";");
                // update scores
                if (answers[this.players.indexOf(player)].equals(this.questions.get(round).getAnswer())) {
                    this.scores[this.players.indexOf(player)] += 1;
                }
            }
        }

        String winner = "";
        int winnerScore = 0;

        // Sending results
        for (Client player : this.players) {
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

    private void printQuestion(Client client, int round) throws Exception {
        Question question = this.questions.get(round);
        StringBuilder questionText = new StringBuilder();
        questionText.append("Round: ").append(round + 1).append("\n");
        questionText.append("Question: ").append(question.getQuestionText()).append("\n");
        questionText.append("Options: ").append(question.getOptions()).append("\n");
        Server.request(client.getSocket(), "OPT", questionText.toString());
    }

    private void printCurrentScores(int currentRound) {
        StringBuilder results = new StringBuilder();
        results.append("Round: ").append(currentRound + 1).append("\n");
        for (Client player : this.players) {
            results.append(player.getUsername()).append("'s Score: ").append(scores[this.players.indexOf(player)]).append("\n");
        }
        this.notifyPlayers("SCORE", results.toString(), null);
    }

    private void notifyPlayers(String messageType, String message, Client excluded) {
        try {
            for (Client client : this.players) {
                if (excluded != null && client.equals(excluded)) continue;
                Server.request(client.getSocket(), messageType, message);
                Connection.receive(client.getSocket());
            }
        } catch (Exception exception) {
            System.out.println("Exception: " + exception.getMessage());
        }
    }


}