package model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * ChatMessage - Immutable value object representing a single chat message.
 * Every message is assigned a globally-unique ID on construction so that
 * read-receipts can reference it later.
 */
public class ChatMessage {

    // ── Message status constants ──────────────────────────────────────────────
    public static final String STATUS_SENDING   = "sending";
    public static final String STATUS_SENT      = "sent";
    public static final String STATUS_DELIVERED = "delivered";
    public static final String STATUS_READ      = "read";
    public static final String STATUS_ERROR     = "error";

    // ── Fields ────────────────────────────────────────────────────────────────
    private final String      messageId;
    private final MessageType type;
    private final String      sender;
    private final String      target;   // null for GROUP messages
    private final String      content;
    private final String      timestamp;
    private volatile String   status;

    // Formatter: "10:30 AM"
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("hh:mm a");

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * Create a new ChatMessage with an auto-generated ID and the current time.
     *
     * @param type    The message type (GROUP or PRIVATE).
     * @param sender  Username of the sender.
     * @param target  Username of the recipient (null for group messages).
     * @param content The message body.
     */
    public ChatMessage(MessageType type, String sender, String target, String content) {
        this.messageId = "msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        this.type      = type;
        this.sender    = sender;
        this.target    = target;
        this.content   = content;
        this.timestamp = LocalDateTime.now().format(TIME_FMT);
        this.status    = STATUS_SENT;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String      getMessageId() { return messageId; }
    public MessageType getType()      { return type; }
    public String      getSender()    { return sender; }
    public String      getTarget()    { return target; }
    public String      getContent()   { return content; }
    public String      getTimestamp() { return timestamp; }
    public String      getStatus()    { return status; }

    /** Thread-safe status update used when a read-receipt arrives. */
    public void setStatus(String status) { this.status = status; }

    // ── JSON helpers ──────────────────────────────────────────────────────────

    /**
     * Serialise this message to a JSON string suitable for sending over
     * a WebSocket to the recipient(s).
     */
    public String toJson() {
        return "{"
                + "\"type\":\"" + type.name() + "\","
                + "\"messageId\":\"" + messageId + "\","
                + "\"sender\":\"" + escapeJson(sender) + "\","
                + (target != null ? "\"target\":\"" + escapeJson(target) + "\"," : "")
                + "\"content\":\"" + escapeJson(content) + "\","
                + "\"timestamp\":\"" + timestamp + "\","
                + "\"status\":\"" + status + "\""
                + "}";
    }

    /** Escape the minimal set of characters required for valid JSON strings. */
    private static String escapeJson(String raw) {
        if (raw == null) return "";
        return raw.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    @Override
    public String toString() {
        return "[" + messageId + "] " + sender
                + (target != null ? " -> " + target : " -> GROUP")
                + ": " + content;
    }
}
