package client;

import config.ServerConfig;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

/**
 * Entry point for the Chat Client.
 *
 * Responsibilities:
 *  - Connect to the Chat Server via TCP Socket
 *  - Start MessageReceiver on a background thread (incoming messages)
 *  - Run a send loop on the main thread (outgoing messages)
 */
public class ChatClient {

    /** Connects to the server and begins the chat session. */
    private void connect() {
        System.out.println("[Client] Connecting to " + ServerConfig.HOST + ":" + ServerConfig.PORT + " ...");

        try (
            Socket         socket = new Socket(ServerConfig.HOST, ServerConfig.PORT);
            PrintWriter    out    = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in     = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            System.out.println("[Client] Connected.\n");

            // Start background thread to continuously receive and print server messages
            Thread receiver = new Thread(new MessageReceiver(in));
            receiver.setDaemon(true); // Dies automatically when main thread exits
            receiver.start();

            sendLoop(out); // Blocking — runs until user types /quit

        } catch (IOException e) {
            System.err.println("[Client] Connection error: " + e.getMessage());
        }

        System.out.println("[Client] Disconnected.");
    }

    /**
     * Reads user input from the keyboard and sends each line to the server.
     * Exits the loop when the user types /quit.
     */
    private void sendLoop(PrintWriter out) {
        try (Scanner scanner = new Scanner(System.in)) {
            while (scanner.hasNextLine()) {
                String input = scanner.nextLine();
                out.println(input);
                if (input.trim().equals("/quit")) break;
            }
        }
    }

    public static void main(String[] args) {
        new ChatClient().connect();
    }
}
