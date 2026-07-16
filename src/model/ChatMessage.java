+package model;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
/*data model representing one chat message */
public final class ChatMessage {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    private final MessageType type;
    private final String      sender;
    private final String      content;
    private final LocalTime   timestamp;

    private ChatMessage(MessageType type, String sender, String content) {
        this.type      = type;
        this.sender    = sender;
        this.content   = content;
        this.timestamp = LocalTime.now();
    }

    /** Factory method — captures timestamp at the moment of creation. */
    public static ChatMessage of(MessageType type, String sender, String content) {
        return new ChatMessage(type, sender, content);
    }

    public MessageType getType()    { return type; }
    public String      getSender()  { return sender; }
    public String      getContent() { return content; }

    /* returns display ready scrren shown in client terminal*/
    public String formatted() {
        return String.format("[%s] [%s] %s", timestamp.format(TIME_FMT), sender, content);
    }

    @Override
    public String toString() { return formatted(); }
}
