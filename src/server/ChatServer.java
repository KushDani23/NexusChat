package server;

import config.ServerConfig;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Entry point for the Chat Server.
 *
 * Responsibilities:
 *  - Open ServerSocket on the configured port
 *  - Maintain a fixed thread pool (ExecutorService)
 *  - Accept incoming client connections in a loop
 *  - Spawn a ClientHandler for each connected client
 */
public class ChatServer {

    private final UserManager    userManager = new UserManager();
    private final ChatLogger     chatLogger  = new ChatLogger();
    private final ExecutorService pool       = Executors.newFixedThreadPool(ServerConfig.MAX_CLIENTS);

    /** Opens the server socket and begins accepting clients. */
    private void start() {
        System.out.println("[Server] Starting on port " + ServerConfig.PORT + " ...");

        try (ServerSocket serverSocket = new ServerSocket(ServerConfig.PORT)) {
            System.out.println("[Server] Ready. Waiting for clients...\n");
            acceptClients(serverSocket);
        } catch (IOException e) {
            System.err.println("[Server] Fatal error: " + e.getMessage());
        } finally {
            pool.shutdown();
            System.out.println("[Server] Shut down.");
        }
    }

    /**
     * Infinite accept loop.
     * Each accepted Socket is wrapped in a ClientHandler and submitted
     * to the thread pool — the main thread immediately loops back to accept().
     */
    private void acceptClients(ServerSocket serverSocket) throws IOException {
        while (true) {
            Socket clientSocket = serverSocket.accept();
            System.out.println("[Server] New connection from " + clientSocket.getInetAddress());
            pool.execute(new ClientHandler(clientSocket, userManager, chatLogger));
        }
    }

    public static void main(String[] args) {
        new ChatServer().start();
    }
}
