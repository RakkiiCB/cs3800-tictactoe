import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.Executors;

/**
 * A server for a multiplayer tic-tac-toe game loosely based on the
 * example provided by cs.lmu.edu. This class runs the server for
 * clients to connect in pairs to play tic-tac-toe. The thread pool
 * allows for multiple games to be run at once.
 */
public class TicTacToeServer {
    public static void main(String[] args) throws Exception {
        try (ServerSocket listener = new ServerSocket(60429)) {
            System.out.println("Tic Tac Toe Server is Running...");
            // limit the number of threads (and games) going at once
            var pool = Executors.newFixedThreadPool(200);
            while (true) {
                Game game = new Game();
                pool.execute(game.new Player(listener.accept(), 'X'));
                pool.execute(game.new Player(listener.accept(), 'O'));
            }
        }
    }
}

/** 
 * Game logic and player logic for tic-tac-toe.
 */
class Game {
    /** 
     * In each instance of the game, a board, represented by an array, 
     * is created with nine cells numbered 0-8 as such:
     * 0 1 2
     * 3 4 5
     * 6 7 8
     * 
     * Each cell contains a reference to a specific player's mark
     * or null if there are no players occupying that cell.
     */
    private Player[] board = new Player[9];

    // All possible win conditions
    private int[][] winConditions = {
        {0, 1, 2},
        {3, 4, 5},
        {6, 7, 8},
        {0, 3, 6},
        {1, 4, 7},
        {2, 5, 8},
        {0, 4, 8},
        {2, 4, 6}
    };

    Player currPlayer;
    
    /**
     * Checks all 8 possible win conditions from the board.
     * @return Whether any of the 8 win conditions contains the same
     *         references to a single player.
     */
    public boolean checkWin() {
        for (int[] w : winConditions) {
            if (board[w[0]] == null) { continue; }
            if (board[w[0]] == board[w[1]] 
                    && board[w[0]] == board[w[2]]) {
                return true;
            } 
        }

        return false;
    }

    /**
     * Check if the board is filled up.
     * A filled-up board results in a tie game.
     * @return If all 9 spaces of the board are filled.
     */
    public boolean checkTie() {
        for (int i = 0; i < board.length; ++i) {
            if (board[i] == null) return false;
        }
        
        return true;
    }

    /**
     * Processes the move given by a player.
     * If the player attempting to make a move isn't the current player
     * (in other words, it's not their turn), or if a cell is already
     * occupied, the move will be rejected.
     * Any move without a second player present will also be rejected.
     * If the move is not rejected, the move will be stored and
     * the opponent player will then take their turn.
     * @param location The selected cell from the moving player.
     * @param player The player attempting the move.
     */
    public synchronized void move(int location, Player player) {
        if (player != currPlayer) {
            throw new IllegalStateException(
                "Not your turn"
            );
        } else if (player.opponent == null) {
            throw new IllegalStateException(
                "You don't have an opponent yet"
            );
        } else if (board[location] != null) {
            throw new IllegalStateException(
                "Cell already occupied"
            );
        }

        // mark the cell with a reference to the current player
        board[location] = currPlayer;

        // pass the turn to the opponent
        currPlayer = currPlayer.opponent;
    }

    /**
     * A Player is identified by a character 'X' or 'O'. For
     * communication with the client, the player has a socket
     * and an associated Scanner and PrintWriter.
     */
    class Player implements Runnable {
        char mark;
        Player opponent;
        Socket socket;
        Scanner input;
        PrintWriter output;

        public Player(Socket socket, char mark) {
            this.socket = socket;
            this.mark = mark;
        }

        @Override
        public void run() {
            try {
                setup();
                processCommands();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (opponent != null && opponent.output != null) {
                    opponent.output.println("OTHER_PLAYER_LEFT");
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        
        // Setup player
        private void setup() throws IOException {
            input = new Scanner(socket.getInputStream());
            output = new PrintWriter(socket.getOutputStream(), true);
            output.println("WELCOME " + mark);
            
            // First player that connects is assigned the X mark
            if (mark == 'X') {
                currPlayer = this;
                output.println("MESSAGE Waiting for opponent to connect");
            } else {
                opponent = currPlayer;
                opponent.opponent = this;
                opponent.output.println("MESSAGE Your move");
            }
        }

        // Player commands
        private void processCommands() {
            while (input.hasNextLine()) {
                var command = input.nextLine();
                if (command.startsWith("QUIT")) {
                    return;
                } else if (command.startsWith("MOVE")) {
                    processMoveCommand(Integer.parseInt(command.substring(5)));
                }
            }
        }

        // Player move commands
        private void processMoveCommand(int location) {
            try {
                move(location, this);
                output.println("VALID_MOVE");
                opponent.output.println("OPPONENT_MOVED " + location);
                if (checkWin()) {
                    output.println("VICTORY");
                    opponent.output.println("DEFEAT");
                } else if (checkTie()) {
                    output.println("TIE");
                    opponent.output.println("TIE");
                }
            } catch (IllegalStateException e) {
                output.println("MESSAGE " + e.getMessage());
            }
        }
    }
}