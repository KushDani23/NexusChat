package server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import config.ServerConfig;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.logging.*;

/**
 * ChatServer - Application entry point.
 *
 * Responsibilities:
 *  1. Start an embedded {@link HttpServer} on port 8080 to serve all
 *     static frontend assets (index.html, CSS, JS).
 *  2. Start the {@link WebSocketHandler} on port 8081 to handle real-time
 *     bidirectional communication.
 *  3. Wire the shared {@link UserManager} and {@link ChatLogger} singletons
 *     into both servers.
 *
 * Run:
 *   java -cp "src;lib/*" server.ChatServer
 * Then open: http://localhost:8080
 */
public class ChatServer {

    private static final Logger LOG = Logger.getLogger(ChatServer.class.getName());

    private HttpServer       httpServer;
    private WebSocketHandler wsHandler;
    private final UserManager userManager = new UserManager();
    private final ChatLogger  chatLogger  = new ChatLogger();

    // ── Entry point ───────────────────────────────────────────────────────────

    public static void main(String[] args) {
        ChatServer server = new ChatServer();
        server.start();

        // Graceful shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutdown signal received – stopping server...");
            server.stop();
        }));
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Initialise and start both the HTTP and WebSocket servers. */
    public void start() {
        startHttpServer();
        startWebSocketServer();

        chatLogger.logInfo("Chat application started.");
        System.out.println("==============================================");
        System.out.println("  Real-Time Chat Application - STARTED");
        System.out.println("==============================================");
        System.out.println("  Frontend : http://localhost:" + ServerConfig.HTTP_PORT);
        System.out.println("  WebSocket: ws://localhost:" + ServerConfig.WS_PORT + ServerConfig.WS_PATH);
        System.out.println("  Log file : " + chatLogger.getLogFilePath());
        System.out.println("==============================================");
    }

    /** Stop both servers gracefully. */
    public void stop() {
        if (wsHandler != null) {
            try { wsHandler.stop(1000); } catch (InterruptedException ignored) {}
        }
        if (httpServer != null) {
            httpServer.stop(1);
        }
        chatLogger.logInfo("Chat application stopped.");
        LOG.info("Chat application stopped.");
    }

    // ── HTTP server ───────────────────────────────────────────────────────────

    private void startHttpServer() {
        try {
            httpServer = HttpServer.create(
                    new InetSocketAddress(ServerConfig.HTTP_PORT), 0);
            httpServer.createContext("/", new StaticFileHandler());
            httpServer.setExecutor(Executors.newFixedThreadPool(4));
            httpServer.start();
            LOG.info("HTTP server started on port " + ServerConfig.HTTP_PORT);
        } catch (IOException e) {
            LOG.severe("Failed to start HTTP server: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    // ── WebSocket server ──────────────────────────────────────────────────────

    private void startWebSocketServer() {
        wsHandler = new WebSocketHandler(ServerConfig.WS_PORT, userManager, chatLogger);
        wsHandler.start();
        LOG.info("WebSocket server started on port " + ServerConfig.WS_PORT);
    }

    // ── Static file handler ───────────────────────────────────────────────────

    /**
     * Serves static files from {@code src/webapp/} (or classpath resources).
     * Handles MIME types for HTML, CSS, JS, and common image formats.
     */
    private static class StaticFileHandler implements HttpHandler {

        private static final Map<String, String> MIME_TYPES = new HashMap<>();

        static {
            MIME_TYPES.put("html", "text/html; charset=UTF-8");
            MIME_TYPES.put("css",  "text/css; charset=UTF-8");
            MIME_TYPES.put("js",   "application/javascript; charset=UTF-8");
            MIME_TYPES.put("json", "application/json");
            MIME_TYPES.put("png",  "image/png");
            MIME_TYPES.put("jpg",  "image/jpeg");
            MIME_TYPES.put("ico",  "image/x-icon");
            MIME_TYPES.put("svg",  "image/svg+xml");
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String requestPath = exchange.getRequestURI().getPath();

            // Map "/" to "/index.html"
            if ("/".equals(requestPath)) requestPath = "/index.html";

            // Strip leading slash and resolve under webapp directory
            String relativePath = requestPath.startsWith("/")
                    ? requestPath.substring(1) : requestPath;
            Path filePath = Paths.get(ServerConfig.WEBAPP_PATH, relativePath);

            if (Files.exists(filePath) && !Files.isDirectory(filePath)) {
                byte[] fileBytes = Files.readAllBytes(filePath);
                String ext = getExtension(filePath.toString());
                String contentType = MIME_TYPES.getOrDefault(ext, "application/octet-stream");

                exchange.getResponseHeaders().set("Content-Type", contentType);
                // Allow any origin (needed for WebSocket upgrade)
                exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                exchange.sendResponseHeaders(200, fileBytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(fileBytes);
                }
            } else {
                // 404 – return a simple error page
                byte[] response = ("<html><body><h1>404 Not Found</h1>"
                        + "<p>Resource not found: " + requestPath + "</p></body></html>")
                        .getBytes("UTF-8");
                exchange.getResponseHeaders().set("Content-Type", "text/html");
                exchange.sendResponseHeaders(404, response.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(response);
                }
            }
        }

        private String getExtension(String filename) {
            int dot = filename.lastIndexOf('.');
            return dot >= 0 ? filename.substring(dot + 1).toLowerCase() : "";
        }
    }
}
