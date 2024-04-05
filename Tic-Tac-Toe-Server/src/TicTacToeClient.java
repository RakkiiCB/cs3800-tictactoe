import java.awt.Font;
import java.awt.Graphics;
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
import javax.swing.Painter;

import javax.imageio.ImageIO;

/**
 * A client for a multiplayer tic-tac-toe game.
 */
public class TicTacToeClient {

    private JFrame frame = new JFrame("Tic Tac Toe");
    private JLabel messageLabel = new JLabel("...");

    private Square[] board = new Square[9];
    private Square currentSquare;

    private static BufferedImage boardImage;
    private static BufferedImage redX;
	private static BufferedImage blueX;
	private static BufferedImage redCircle;
	private static BufferedImage blueCircle;

    private Painter painter;

    private Socket socket;

    private static Scanner kb;
    private Scanner in;
    private PrintWriter out;

    private static final int WINDOW_WIDTH = 506;
    private static final int WINDOW_HEIGHT = 527;

    private int lenOfSpace = 160;


    public TicTacToeClient(String serverAddress) throws Exception {

        socket = new Socket(serverAddress, 60429);
        in = new Scanner(socket.getInputStream());
        out = new PrintWriter(socket.getOutputStream(), true);

        loadImages();

        messageLabel.setBackground(Color.lightGray);
        frame.getContentPane().add(messageLabel, BorderLayout.SOUTH);

        var boardPanel = new JPanel();
        boardPanel.setBackground(Color.black);
        boardPanel.setLayout(new GridLayout(3, 3, 2, 2));
        for (var i = 0; i < board.length; i++) {
            final int j = i;
            board[i] = new Square();
            board[i].addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    currentSquare = board[j];
                    out.println("MOVE " + j);
                }
            });
            boardPanel.add(board[i]);
        }
        frame.getContentPane().add(boardPanel, BorderLayout.CENTER);
    }

    private void loadImages() {
        try {
			boardImage = ImageIO.read(getClass().getResourceAsStream("/tictactoe-board.png"));
			redX = ImageIO.read(getClass().getResourceAsStream("/red-x.png"));
			redCircle = ImageIO.read(getClass().getResourceAsStream("/red-circle.png"));
			blueX = ImageIO.read(getClass().getResourceAsStream("/blue-x.png"));
			blueCircle = ImageIO.read(getClass().getResourceAsStream("/blue-circle.png"));
		} catch (IOException e) {
			e.printStackTrace();
		}
    }

    /**
     * 
     */
    public void play() throws Exception {
        try {
            var response = in.nextLine();
            var mark = response.charAt(8);
            var opponentMark = mark == 'X' ? 'O' : 'X';
            frame.setTitle("Tic Tac Toe: Player " + mark);
            while (in.hasNextLine()) {
                response = in.nextLine();
                if (response.startsWith("VALID_MOVE")) {
                    messageLabel.setText("Valid move, please wait");
                    currentSquare.setImage(mark);
                    currentSquare.repaint();
                } else if (response.startsWith("OPPONENT_MOVED")) {
                    var loc = Integer.parseInt(response.substring(15));
                    board[loc].setImage(opponentMark);
                    board[loc].repaint();
                    messageLabel.setText("Opponent moved, your turn");
                } else if (response.startsWith("MESSAGE")) {
                    messageLabel.setText(response.substring(8));
                } else if (response.startsWith("VICTORY")) {
                    JOptionPane.showMessageDialog(frame, "You Won!");
                    break;
                } else if (response.startsWith("DEFEAT")) {
                    JOptionPane.showMessageDialog(frame, "You Lost.");
                    break;
                } else if (response.startsWith("TIE")) {
                    JOptionPane.showMessageDialog(frame, "It's a tie.");
                    break;
                } else if (response.startsWith("OTHER_PLAYER_LEFT")) {
                    JOptionPane.showMessageDialog(frame, "Other player left");
                    break;
                }
            }
            out.println("QUIT");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            socket.close();
            frame.dispose();
        }
    }

    static class Square extends JPanel {
        JLabel label = new JLabel();

        public Square() {
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

    public static void main(String[] args) throws Exception {

        String ipString = "";
        final String IPV4_REGEX =
                    "^(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\." +
                    "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\." +
                    "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\." +
                    "(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
        final Pattern IPv4_PATTERN = Pattern.compile(IPV4_REGEX);    

        /** 
         * If the user doesn't provide a server IP in the command
         * line, prompt them in the program itself.
         * Otherwise, the argument provided in the command line
         * will be used as the server IP.
         */
    	if (args.length == 0) {
            kb = new Scanner(System.in);
    		System.out.println("Please enter the server IP: ");

            String ip = kb.nextLine();
    		Matcher matcher = IPv4_PATTERN.matcher(ip);

            if (matcher.matches()) {
                ipString = ip;
            } else {
                System.err.println(
                    "The IP is invalid. Please enter a valid IP\n"
                    + "or pass a valid server IP as a the sole command "
                    + "line argument."
                );

                return;
            }
    	} else if (args.length == 1){
            ipString = args[0];
        } else {
            System.err.println("Pass the server IP as the sole command line argument.");
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