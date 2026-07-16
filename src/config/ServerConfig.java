package config;

/**
 * Centralized configuration constants for the Chat Application.
 * All server and client components reference these values.
 */
public final class ServerConfig {

    public static final String HOST     = "localhost";
    public static final int    PORT     = 12345;
    public static final int    MAX_CLIENTS = 50;
    public static final String LOG_FILE = "logs/chat_history.log";

    // Prevent instantiation — this is a constants class
    private ServerConfig() {}
}
