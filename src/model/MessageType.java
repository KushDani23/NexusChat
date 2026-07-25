package model;

/**
 * MessageType - Enum representing all supported WebSocket message types.
 * Used for serialisation/deserialisation of JSON messages between
 * client and server.
 */
public enum MessageType {

    // ── Client → Server ──────────────────────────────────────────────────────
    /** Initial user registration with chosen username. */
    REGISTER,

    /** Send a message to the group chat. */
    GROUP,

    /** Send a private message to a specific user. */
    PRIVATE,

    /** Notify that the user is (or has stopped) typing. */
    TYPING,

    /** Request the current list of online users. */
    GET_USERS,

    /** Acknowledge that a message has been read. */
    READ_RECEIPT,

    // ── Server → Client ──────────────────────────────────────────────────────
    /** Response to a REGISTER request. */
    REGISTER_RESPONSE,

    /** Deliver a group message to all connected clients. */
    // GROUP (reused)

    /** Deliver a private message to the target client. */
    // PRIVATE (reused)

    /** Broadcast that a user's typing state has changed. */
    // TYPING (reused)

    /** Deliver the current online-users list to the requesting client. */
    USERS_LIST,

    /** Broadcast that a new user has connected. */
    USER_JOINED,

    /** Broadcast that a user has disconnected. */
    USER_LEFT,

    /** Notify the original sender of a read-receipt update. */
    // READ_RECEIPT (reused)

    /** Update the delivery/read status of a specific message. */
    MESSAGE_STATUS,

    /** Generic error message. */
    ERROR
}
