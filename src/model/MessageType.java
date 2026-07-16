package model;
public enum MessageType {
    GROUP,    // broadcast
    PRIVATE,  //private message /msg
    JOIN,     // notification for user has joined
    LEAVE,    // notification for user has leaveed
    COMMAND,  // client side command
    INFO      // server info reply
}
