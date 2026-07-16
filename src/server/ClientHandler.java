package server;

import model.ChatMessage;
import model.MessageType;

import java.io.*;
import java.net.Socket;

/**
 * Handles the full lifecycle of one connected client on its own thread.
 *
 * Lifecycle: register username → broadcast JOIN → read loop → cleanup on exit.
 */
public class ClientHandler implements Runnable {

    private final Socket      socket;
    private final UserManager userManager;
    private final ChatLogger  chatLogger;

    private PrintWriter out;
    private String      username;

    public ClientHandler(Socket socket, UserManager userManager, ChatLogger chatLogger) {
        this.socket      = socket;
        this.userManager = userManager;
        this.chatLogger  = chatLogger;
    }

    @Override
    public void run() {
        try (
            BufferedReader in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter    out = new PrintWriter(socket.getOutputStream(), true)
        ) {
            this.out = out;

            if (!register(in)) return; // Username registration failed — close connection

            // Notify all users and log the join
            ChatMessage joinMsg = ChatMessage.of(MessageType.JOIN, "Server",
                    username + " has joined the chat.");
            userManager.broadcast(joinMsg);
            chatLogger.log(joinMsg);

            // Main read loop — runs until client disconnects or sends /quit
            String line;
            while ((line = in.readLine()) != null) {
                handleMessage(line.trim());
            }

        } catch (IOException e) {
            // Client disconnected abruptly — handled in finally via cleanup()
        } finally {
            cleanup();
        }
    }

    /**
     * Prompts the client for a unique username.
     * Retries up to 3 times before closing the connection.
     *
     * @return true if registration succeeded, false otherwise
     */
    private boolean register(BufferedReader in) throws IOException {
        out.println("Enter your username:");
        for (int attempt = 0; attempt < 3; attempt++) {
            String name = in.readLine();
            if (name == null) return false;
            name = name.trim();
            if (userManager.addUser(name, this)) {
                this.username = name;
                out.println("Welcome, " + username + "! Type /users to see who's online.");
                return true;
            }
            out.println("Username '" + name + "' is taken. Try another:");
        }
        out.println("Too many failed attempts. Disconnecting.");
        return false;
    }

    /**
     * Routes incoming text to the correct handler:
     *  /msg <user> <message> → private message
     *  /users               → list online users
     *  /quit                → graceful disconnect
     *  anything else        → group broadcast
     */
    private void handleMessage(String input) {
        if (input.startsWith("/msg ")) {
            sendPrivate(input.substring(5));
        } else if (input.equals("/users")) {
            out.println("[Server] Online: " + userManager.getUserList());
        } else if (input.equals("/quit")) {
            cleanup();
        } else if (!input.isEmpty()) {
            ChatMessage msg = ChatMessage.of(MessageType.GROUP, username, input);
            userManager.broadcast(msg);
            chatLogger.log(msg);
        }
    }

    /**
     * Parses and delivers a private message.
     * Format expected: "<targetUser> <message>"
     */
    private void sendPrivate(String args) {
        int space = args.indexOf(' ');
        if (space == -1) {
            out.println("[Server] Usage: /msg <username> <message>");
            return;
        }
        String target  = args.substring(0, space);
        String content = args.substring(space + 1);

        ClientHandler targetHandler = userManager.getUser(target);
        if (targetHandler == null) {
            out.println("[Server] User '" + target + "' not found or offline.");
            return;
        }
        ChatMessage pm = ChatMessage.of(MessageType.PRIVATE, username,
                "(private → " + target + ") " + content);
        targetHandler.send("[PM from " + username + "] " + content);
        send(pm.formatted()); // Echo to sender with timestamp
        chatLogger.log(pm);
    }

    /** Writes one line to this client's output stream. */
    public void send(String message) {
        if (out != null) out.println(message);
    }

    /**
     * Removes this client from UserManager, notifies remaining users,
     * and closes the socket. Safe to call multiple times.
     */
    private void cleanup() {
        if (username != null && userManager.removeUser(username)) {
            ChatMessage leaveMsg = ChatMessage.of(MessageType.LEAVE, "Server",
                    username + " has left the chat.");
            userManager.broadcast(leaveMsg);
            chatLogger.log(leaveMsg);
            username = null; // Prevent duplicate cleanup
        }
        try { socket.close(); } catch (IOException ignored) {}
    }
}
