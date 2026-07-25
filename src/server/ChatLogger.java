package server;

import config.ServerConfig;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.*;

/**
 * ChatLogger - Writes all chat activity to a timestamped log file.
 *
 * Every event (join, leave, group message, private message, typing,
 * read-receipt) is appended to {@code logs/chat_history.log} with a
 * consistent, human-readable format.
 */
public class ChatLogger {

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Logger JAVA_LOG = Logger.getLogger(ChatLogger.class.getName());

    private final String logFilePath;
    private final Object fileLock = new Object(); // guards file I/O

    // ── Constructor ───────────────────────────────────────────────────────────

    public ChatLogger() {
        this.logFilePath = ServerConfig.LOG_FILE;
        ensureLogDirectory();
    }

    // ── Public logging methods ────────────────────────────────────────────────

    /** Log a user joining the chat. */
    public void logJoin(String username) {
        write("[INFO] User '" + username + "' joined the chat");
    }

    /** Log a user leaving the chat. */
    public void logLeave(String username) {
        write("[INFO] User '" + username + "' left the chat");
    }

    /** Log a group message. */
    public void logGroupMessage(String sender, String content) {
        write("[GROUP] " + sender + ": " + content);
    }

    /** Log a private message. */
    public void logPrivateMessage(String sender, String target, String content) {
        write("[PRIVATE] " + sender + " -> " + target + ": " + content);
    }

    /** Log a typing notification. */
    public void logTyping(String sender, String target, boolean isTyping) {
        if (isTyping) {
            write("[TYPING] " + sender + " is typing" +
                  (target != null ? " to " + target : " in group") + "...");
        }
    }

    /** Log a read-receipt event. */
    public void logReadReceipt(String reader, String messageId) {
        write("[READ] " + reader + " read message " + messageId);
        write("[STATUS] Message " + messageId + " marked as read");
    }

    /** Log a message status update (sent / delivered / read). */
    public void logMessageStatus(String messageId, String sender, String status) {
        write("[STATUS] Message " + messageId + " sent by " + sender +
              " - status: " + status);
    }

    /** Log an arbitrary info string. */
    public void logInfo(String message) {
        write("[INFO] " + message);
    }

    /** Log an error. */
    public void logError(String message) {
        write("[ERROR] " + message);
    }

    /** Return the configured log file path. */
    public String getLogFilePath() { return logFilePath; }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void write(String message) {
        String line = LocalDateTime.now().format(DT_FMT) + " - " + message;
        System.out.println(line); // also echo to console

        synchronized (fileLock) {
            try (BufferedWriter bw = Files.newBufferedWriter(
                    Paths.get(logFilePath),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND)) {
                bw.write(line);
                bw.newLine();
            } catch (IOException e) {
                JAVA_LOG.warning("Failed to write log entry: " + e.getMessage());
            }
        }
    }

    private void ensureLogDirectory() {
        try {
            Files.createDirectories(Paths.get(ServerConfig.LOG_DIR));
        } catch (IOException e) {
            JAVA_LOG.warning("Could not create log directory: " + e.getMessage());
        }
    }
}
