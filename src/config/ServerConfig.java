package config;

/**
 * ServerConfig - Centralized configuration for the Chat Server.
 * Holds all server-wide constants such as ports, paths, and limits.
 */
public class ServerConfig {

    // HTTP server port (serves index.html and static assets)
    public static final int HTTP_PORT = 8080;

    // WebSocket server port
    public static final int WS_PORT = 8081;

    // WebSocket endpoint path
    public static final String WS_PATH = "/chat";

    // Path to the webapp (static files served by HTTP server)
    public static final String WEBAPP_PATH = "src/webapp";

    // Log directory and file
    public static final String LOG_DIR  = "logs";
    public static final String LOG_FILE = "logs/chat_history.log";

    // Maximum username length
    public static final int MAX_USERNAME_LENGTH = 20;

    // Minimum username length
    public static final int MIN_USERNAME_LENGTH = 2;

    // Private constructor – utility class should not be instantiated
    private ServerConfig() {}
}
