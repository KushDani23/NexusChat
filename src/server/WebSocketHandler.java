package server;

import config.ServerConfig;
import model.ChatMessage;
import model.MessageType;
import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * WebSocketHandler - Core WebSocket server.
 *
 * Extends {@link WebSocketServer} from the Java-WebSocket library and handles
 * every client lifecycle event: open, message, close, and error.
 *
 * Routing logic:
 *   REGISTER      → handleRegistration()
 *   GROUP         → handleGroupMessage()
 *   PRIVATE       → handlePrivateMessage()
 *   TYPING        → handleTypingIndicator()
 *   GET_USERS     → sendUsersList()
 *   READ_RECEIPT  → handleReadReceipt()
 */
public class WebSocketHandler extends WebSocketServer {

    private static final Logger LOG = Logger.getLogger(WebSocketHandler.class.getName());

    private final UserManager userManager;
    private final ChatLogger  chatLogger;

    // Tracks all messages sent so we can update their status later.
    // messageId → ChatMessage
    private final ConcurrentHashMap<String, ChatMessage> messageStore = new ConcurrentHashMap<>();

    // ── Constructor ───────────────────────────────────────────────────────────

    public WebSocketHandler(int port, UserManager userManager, ChatLogger chatLogger) {
        super(new InetSocketAddress(port));
        this.userManager = userManager;
        this.chatLogger  = chatLogger;
        setReuseAddr(true);
    }

    // ── WebSocketServer lifecycle ─────────────────────────────────────────────

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        LOG.info("New WebSocket connection from: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket conn, String rawMessage) {
        try {
            String type = extractJsonField(rawMessage, "type");
            if (type == null) {
                sendError(conn, "Missing 'type' field in message.");
                return;
            }

            switch (type.toUpperCase()) {
                case "REGISTER":     handleRegistration(conn, rawMessage);    break;
                case "GROUP":        handleGroupMessage(conn, rawMessage);     break;
                case "PRIVATE":      handlePrivateMessage(conn, rawMessage);   break;
                case "TYPING":       handleTypingIndicator(conn, rawMessage);  break;
                case "GET_USERS":    sendUsersList(conn);                      break;
                case "READ_RECEIPT": handleReadReceipt(conn, rawMessage);      break;
                default:
                    sendError(conn, "Unknown message type: " + type);
            }
        } catch (Exception e) {
            LOG.warning("Error processing message: " + e.getMessage());
            sendError(conn, "Server error processing your message.");
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String username = userManager.removeUserBySession(conn);
        if (username != null) {
            chatLogger.logLeave(username);
            broadcastUserLeft(username);
            broadcastUsersList();
            LOG.info("User '" + username + "' disconnected (code=" + code + ").");
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        LOG.warning("WebSocket error: " + ex.getMessage());
        chatLogger.logError("WebSocket error: " + ex.getMessage());
    }

    @Override
    public void onStart() {
        LOG.info("WebSocket server started on port " + getPort());
        chatLogger.logInfo("WebSocket server started on port " + getPort());
    }

    // ── Message handlers ──────────────────────────────────────────────────────

    /** Process a REGISTER request from a newly connected client. */
    private void handleRegistration(WebSocket conn, String json) {
        String username = extractJsonField(json, "username");

        if (username == null || username.trim().isEmpty()) {
            sendMessage(conn, "{\"type\":\"REGISTER_RESPONSE\",\"success\":false,"
                    + "\"error\":\"Username cannot be empty.\"}");
            return;
        }

        username = username.trim();

        // Length validation
        if (username.length() < ServerConfig.MIN_USERNAME_LENGTH
                || username.length() > ServerConfig.MAX_USERNAME_LENGTH) {
            sendMessage(conn, "{\"type\":\"REGISTER_RESPONSE\",\"success\":false,"
                    + "\"error\":\"Username must be between "
                    + ServerConfig.MIN_USERNAME_LENGTH + " and "
                    + ServerConfig.MAX_USERNAME_LENGTH + " characters.\"}");
            return;
        }

        // Uniqueness check
        if (userManager.isUsernameTaken(username)) {
            sendMessage(conn, "{\"type\":\"REGISTER_RESPONSE\",\"success\":false,"
                    + "\"error\":\"Username '" + escapeJson(username) + "' is already taken.\"}");
            return;
        }

        // Register the user
        boolean added = userManager.addUser(username, conn);
        if (!added) {
            // Lost the race – another thread registered the same name
            sendMessage(conn, "{\"type\":\"REGISTER_RESPONSE\",\"success\":false,"
                    + "\"error\":\"Username '" + escapeJson(username) + "' is already taken.\"}");
            return;
        }

        chatLogger.logJoin(username);

        // Confirm registration to the new client
        sendMessage(conn, "{\"type\":\"REGISTER_RESPONSE\",\"success\":true,"
                + "\"username\":\"" + escapeJson(username) + "\"}");

        // Send current users list to the new client
        sendUsersList(conn);

        // Notify everyone else that this user joined
        broadcastUserJoined(username, conn);

        // Broadcast updated users list to all
        broadcastUsersList();

        LOG.info("User '" + username + "' registered successfully.");
    }

    /** Broadcast a group message to all connected users. */
    private void handleGroupMessage(WebSocket conn, String json) {
        String sender  = userManager.getUsername(conn);
        String content = extractJsonField(json, "content");

        if (sender == null || content == null || content.trim().isEmpty()) return;
        content = content.trim();

        ChatMessage msg = new ChatMessage(MessageType.GROUP, sender, null, content);
        messageStore.put(msg.getMessageId(), msg);
        chatLogger.logGroupMessage(sender, content);

        // Forward to all clients
        String msgJson = msg.toJson();
        for (WebSocket session : userManager.getAllSessions()) {
            sendMessage(session, msgJson);
        }

        // Tell the sender that the message was delivered (sent → delivered)
        msg.setStatus(ChatMessage.STATUS_DELIVERED);
        sendMessageStatus(conn, msg.getMessageId(), ChatMessage.STATUS_DELIVERED);
    }

    /** Route a private message to the target user only. */
    private void handlePrivateMessage(WebSocket conn, String json) {
        String sender  = userManager.getUsername(conn);
        String target  = extractJsonField(json, "target");
        String content = extractJsonField(json, "content");

        if (sender == null || target == null || content == null || content.trim().isEmpty()) return;
        content = content.trim();

        WebSocket targetSession = userManager.getUserSession(target);
        if (targetSession == null) {
            sendError(conn, "User '" + target + "' is not online.");
            chatLogger.logError("Failed to send message to " + target + ": User not found");
            return;
        }

        ChatMessage msg = new ChatMessage(MessageType.PRIVATE, sender, target, content);
        messageStore.put(msg.getMessageId(), msg);
        chatLogger.logPrivateMessage(sender, target, content);

        // Deliver to the target
        sendMessage(targetSession, msg.toJson());

        // Confirm "sent" status to sender
        sendMessageStatus(conn, msg.getMessageId(), ChatMessage.STATUS_SENT);
    }

    /** Forward a typing indicator to the appropriate target. */
    private void handleTypingIndicator(WebSocket conn, String json) {
        String sender   = userManager.getUsername(conn);
        String target   = extractJsonField(json, "target");
        String isTyping = extractJsonField(json, "isTyping");

        if (sender == null) return;

        boolean typing = "true".equalsIgnoreCase(isTyping);
        chatLogger.logTyping(sender, target, typing);

        String typingJson = "{\"type\":\"TYPING\","
                + "\"sender\":\"" + escapeJson(sender) + "\","
                + "\"isTyping\":" + typing
                + (target != null ? ",\"target\":\"" + escapeJson(target) + "\"" : "")
                + "}";

        if (target != null) {
            // Private chat typing indicator
            WebSocket targetSession = userManager.getUserSession(target);
            if (targetSession != null) sendMessage(targetSession, typingJson);
        } else {
            // Group chat typing indicator – broadcast to everyone except sender
            for (WebSocket session : userManager.getAllSessions()) {
                if (!session.equals(conn)) sendMessage(session, typingJson);
            }
        }
    }

    /**
     * Handle an incoming READ_RECEIPT from a client.
     * Updates the stored message status and notifies the original sender.
     */
    private void handleReadReceipt(WebSocket conn, String json) {
        String reader    = userManager.getUsername(conn);
        String messageId = extractJsonField(json, "messageId");
        String sender    = extractJsonField(json, "sender");

        if (reader == null || messageId == null) return;

        chatLogger.logReadReceipt(reader, messageId);

        // Update stored message if we have it
        ChatMessage msg = messageStore.get(messageId);
        if (msg != null) {
            msg.setStatus(ChatMessage.STATUS_READ);
            sender = msg.getSender();
        }

        // Forward read-receipt to the original sender
        if (sender != null) {
            WebSocket senderSession = userManager.getUserSession(sender);
            if (senderSession != null) {
                String receiptJson = "{\"type\":\"MESSAGE_STATUS\","
                        + "\"messageId\":\"" + messageId + "\","
                        + "\"status\":\"read\","
                        + "\"reader\":\"" + escapeJson(reader) + "\"}";
                sendMessage(senderSession, receiptJson);
            }
        }
    }

    // ── Broadcast helpers ─────────────────────────────────────────────────────

    /** Send the current online-users list to a single client. */
    private void sendUsersList(WebSocket conn) {
        List<String> users = userManager.getAllUsers();
        StringBuilder sb = new StringBuilder("{\"type\":\"USERS_LIST\",\"users\":[");
        for (int i = 0; i < users.size(); i++) {
            sb.append("\"").append(escapeJson(users.get(i))).append("\"");
            if (i < users.size() - 1) sb.append(",");
        }
        sb.append("]}");
        sendMessage(conn, sb.toString());
    }

    /** Broadcast the current online-users list to all connected clients. */
    private void broadcastUsersList() {
        List<String> users = userManager.getAllUsers();
        StringBuilder sb = new StringBuilder("{\"type\":\"USERS_LIST\",\"users\":[");
        for (int i = 0; i < users.size(); i++) {
            sb.append("\"").append(escapeJson(users.get(i))).append("\"");
            if (i < users.size() - 1) sb.append(",");
        }
        sb.append("]}");
        String json = sb.toString();
        for (WebSocket session : userManager.getAllSessions()) {
            sendMessage(session, json);
        }
    }

    /** Broadcast a USER_JOINED notification to all clients except the new one. */
    private void broadcastUserJoined(String username, WebSocket exclude) {
        String json = "{\"type\":\"USER_JOINED\","
                + "\"username\":\"" + escapeJson(username) + "\"}";
        for (WebSocket session : userManager.getAllSessions()) {
            if (!session.equals(exclude)) sendMessage(session, json);
        }
    }

    /** Broadcast a USER_LEFT notification to all remaining clients. */
    private void broadcastUserLeft(String username) {
        String json = "{\"type\":\"USER_LEFT\","
                + "\"username\":\"" + escapeJson(username) + "\"}";
        for (WebSocket session : userManager.getAllSessions()) {
            sendMessage(session, json);
        }
    }

    /** Send a MESSAGE_STATUS update to a specific client. */
    private void sendMessageStatus(WebSocket conn, String messageId, String status) {
        String json = "{\"type\":\"MESSAGE_STATUS\","
                + "\"messageId\":\"" + messageId + "\","
                + "\"status\":\"" + status + "\"}";
        sendMessage(conn, json);
    }

    /** Send an ERROR message to a client. */
    private void sendError(WebSocket conn, String errorText) {
        String json = "{\"type\":\"ERROR\","
                + "\"message\":\"" + escapeJson(errorText) + "\"}";
        sendMessage(conn, json);
    }

    // ── Low-level send ────────────────────────────────────────────────────────

    /** Safely send a string over a WebSocket, guarding against closed connections. */
    private void sendMessage(WebSocket conn, String json) {
        try {
            if (conn != null && conn.isOpen()) {
                conn.send(json);
            }
        } catch (Exception e) {
            LOG.warning("Failed to send message: " + e.getMessage());
        }
    }

    // ── Minimal JSON parser ───────────────────────────────────────────────────

    /**
     * Extract the string value of a JSON field from a flat JSON object.
     * This avoids a full JSON library dependency for simple parsing needs.
     *
     * NOTE: This parser handles basic string values only. It does NOT handle
     * nested objects or arrays as field values.
     *
     * @param json  Raw JSON string.
     * @param field Field name to extract.
     * @return The unescaped string value, or {@code null} if not found.
     */
    static String extractJsonField(String json, String field) {
        if (json == null || field == null) return null;
        String key = "\"" + field + "\"";
        int keyIdx = json.indexOf(key);
        if (keyIdx < 0) return null;

        int colonIdx = json.indexOf(':', keyIdx + key.length());
        if (colonIdx < 0) return null;

        // Skip whitespace after colon
        int valueStart = colonIdx + 1;
        while (valueStart < json.length() && Character.isWhitespace(json.charAt(valueStart))) {
            valueStart++;
        }
        if (valueStart >= json.length()) return null;

        char firstChar = json.charAt(valueStart);

        if (firstChar == '"') {
            // String value – find the closing quote (respecting escapes)
            StringBuilder sb = new StringBuilder();
            int i = valueStart + 1;
            while (i < json.length()) {
                char c = json.charAt(i);
                if (c == '\\' && i + 1 < json.length()) {
                    char next = json.charAt(i + 1);
                    switch (next) {
                        case '"':  sb.append('"');  i += 2; break;
                        case '\\': sb.append('\\'); i += 2; break;
                        case 'n':  sb.append('\n'); i += 2; break;
                        case 'r':  sb.append('\r'); i += 2; break;
                        case 't':  sb.append('\t'); i += 2; break;
                        default:   sb.append(c);    i++;    break;
                    }
                } else if (c == '"') {
                    break;
                } else {
                    sb.append(c);
                    i++;
                }
            }
            return sb.toString();
        } else {
            // Non-string value (boolean, number) – read until delimiter
            int end = valueStart;
            while (end < json.length()) {
                char c = json.charAt(end);
                if (c == ',' || c == '}' || c == ']' || Character.isWhitespace(c)) break;
                end++;
            }
            return json.substring(valueStart, end);
        }
    }

    /** Escape special characters for JSON string output. */
    private static String escapeJson(String raw) {
        if (raw == null) return "";
        return raw.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
