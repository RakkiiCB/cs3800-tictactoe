import java.awt.Color;
import java.awt.GridLayout;
import java.awt.GridBagLayout;
import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import javax.imageio.ImageIO;

/**
 * A client for a multiplayer tic-tac-toe game loosely based on the
 * example provided by cs.lmu.edu with custom visual changes.
 * Two separate instances connect to each other via the server
 * to play tic-tac-toe.
 */
public class TicTacToeClient {
    private static String ipString;
    // Display window of the game
    private JFrame frame = new JFrame("Tic Tac Toe");
    // JLabel to display messages from the game
    private JLabel messageLabel = new JLabel("Standby...");
    // Use of array as the tic-tac-toe board
    private Cell[] board = new Cell[9];
    //Square that is clicked by player
    private Cell currCell;
    //Image assets
    private static BufferedImage redX;
	private static BufferedImage blueCircle;

    //Socket to communicate with server
    private Socket socket;

    private static Scanner kb;
    //Read input from server
    private Scanner in;
    //Send output to server
    private PrintWriter out;

    private static final int WINDOW_WIDTH = 506;
    private static final int WINDOW_HEIGHT = 527;

    public TicTacToeClient(String serverAddress) throws Exception {
        // Server connection
        socket = new Socket(serverAddress, 60429);
        in = new Scanner(socket.getInputStream());
        out = new PrintWriter(socket.getOutputStream(), true);

        loadImages();

        // Message label at the bottom
        messageLabel.setBackground(Color.lightGray);
        frame.getContentPane().add(messageLabel, BorderLayout.SOUTH);

        // Game board panel
        JPanel boardPanel = new JPanel();
        boardPanel.setBackground(Color.black);
        boardPanel.setLayout(new GridLayout(3, 3, 2, 2));
        
        for (int i = 0; i < board.length; i++) {
            final int CURR_MOVE = i;

            board[i] = new Cell();
            board[i].addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    // Set moved cell
                    currCell = board[CURR_MOVE];
                    // Notify server of player move
                    out.println("MOVE " + CURR_MOVE); 
                }
            });

            boardPanel.add(board[i]);
        }

        frame.getContentPane().add(boardPanel, BorderLayout.CENTER);
    }

    /** 
     * Loads the custom X and O images provided in the source folder.
     */
    private void loadImages() {
        try {
			redX = ImageIO.read(getClass().getResourceAsStream("/red-x.png"));
			blueCircle = ImageIO.read(getClass().getResourceAsStream("/blue-circle.png"));
		} catch (IOException e) {
			e.printStackTrace();
		}
    }

    /**
     * Initiates the game and processes messages sent to and from
     * each player's client to manage each game state.
     */
    public void play() throws Exception {
        boolean replay = true;
        while (replay) {
            try {
                // Wait for the server to send the initial message
                while (!in.hasNextLine()) {
                    Thread.sleep(100);
                }

                String response = in.nextLine();
                char mark = response.charAt(8);
                char opponentMark = mark == 'X' ? 'O' : 'X';

                frame.setTitle("Tic Tac Toe: Player " + mark);

                // Parse different types of messages coming in
                while (in.hasNextLine()) {
                    response = in.nextLine();
                    
                    if (response.startsWith("VALID_MOVE")) {
                        //Display player's move
                        messageLabel.setText("Valid move, please wait");
                        currCell.setImage(mark); 
                        currCell.repaint();
                    } else if (response.startsWith("OPPONENT_MOVED")) {
                         //Display opponent's move
                        int loc = Integer.parseInt(
                            response.substring(15)
                        );
                        board[loc].setImage(opponentMark);
                        board[loc].repaint();
                        messageLabel.setText(
                            "Opponent moved, your turn"
                        );
                    } else if (response.startsWith("MESSAGE")) {
                        messageLabel.setText(
                            response.substring(8)
                        );
                    } else if (response.startsWith("VICTORY")) {
                        JOptionPane.showMessageDialog(
                            frame, "You Won!"
                        );

                        break;
                    } else if (response.startsWith("DEFEAT")) {
                        JOptionPane.showMessageDialog(
                            frame, "You Lost."
                        );
                        
                        break;
                    } else if (response.startsWith("TIE")) {
                        JOptionPane.showMessageDialog(
                            frame, "It's a tie."
                        );

                        break;
                    } else if (response.startsWith("OTHER_PLAYER_LEFT")) {
                        JOptionPane.showMessageDialog(
                            frame, "Opponent disconnnected."
                        );

                        break;
                    }
                }

                // Prompt the players to start a new game or quit.
                int choice = JOptionPane.showConfirmDialog(
                    frame, 
                    "Do you want to replay the game?", 
                    "Replay", JOptionPane.YES_NO_OPTION
                );
                replay = (choice == JOptionPane.YES_OPTION);

                if (replay) {
                    resetBoard();
                    out.println("REPLAY");
                } else {
                    out.println("QUIT"); // Client session ending, notify server
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                socket.close();
                frame.dispose();
            }
        }
    }

    /** 
     * Resets the board and starts a new game.
     */
    private void resetBoard(){
        // Reset all the cells on the board
        for (Cell cell : board) {
            cell.label.setIcon(null);
        }
        // Reset the message label
        messageLabel.setText("Standby...");
        try {
            main(new String[]{ipString});
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** 
     * Visual representation of a cell on the tic-tac-toe board.
     */
    static class Cell extends JPanel {
        JLabel label = new JLabel();

        public Cell() {
            setBackground(Color.white);
            setLayout(new GridBagLayout());
            add(label);
        }

        // adds a custom image for the Xs and Os
        public void setImage(char mark) {
            if (mark == 'X') {
                label.setIcon(new ImageIcon(redX));
            } else {
                label.setIcon(new ImageIcon(blueCircle));
            }
        }
    }

    /** 
     * Creates an instance of a client using the given IP address.
     * If the user doesn't provide a server IP in the command
     * line, prompt them in the program itself.
     * Otherwise, the first argument provided in the command line
     * will be used as the server IP.
     */
    public static void main(String[] args) throws Exception {

        String ipString = "";
        final String IPV4_REGEX =
                    "^(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\." +
                    "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\." +
                    "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\." +
                    "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
        final Pattern IPv4_PATTERN = Pattern.compile(IPV4_REGEX);    

        // Prompt user for server IP method if none is given beforehand
    	if (args.length == 0) {
            kb = new Scanner(System.in);
    		System.out.println("Please enter the server IP: ");

            ipString = kb.nextLine();
    	} else {
            ipString = args[0];
        }

        /** 
         * Print error message if more than one argument is provided,
         * but let it slide and just accept the first argument.
         */
        if (args.length > 1) {
            System.out.println(
                "Only one command line argument is required.\n" +
                "The first argument will be used as the server IP."
            );

            return;
        }
        
        // take out leading/trailing whitespace
        ipString = ipString.strip(); 

        /** 
         * End program immediately if the IP address is invalid.
         * "localhost" is a valid address, so that's an exception
         * to the ip string format matching.
         */
        Matcher matcher = IPv4_PATTERN.matcher(ipString);


        if (!matcher.matches() && !ipString.equals("localhost")) {
            System.err.println(
                "The IP is invalid. Please enter a valid IP\n"
                + "or pass a valid server IP as the first command "
                + "line argument."
            );

            return;
        }

        TicTacToeClient client = new TicTacToeClient(ipString);
        client.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        client.frame.setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        client.frame.setVisible(true);
        client.frame.setResizable(false);
        client.play();
    }
}