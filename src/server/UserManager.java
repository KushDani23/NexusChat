package server;

import org.java_websocket.WebSocket;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * UserManager - Thread-safe registry of all currently connected users.
 *
 * Stores a bidirectional mapping between:
 *   username  →  WebSocket session
 *   WebSocket →  username
 *
 * All operations are safe for concurrent use from multiple handler threads.
 */
public class UserManager {

    // Primary map: username → WebSocket session
    private final ConcurrentHashMap<String, WebSocket> userSessions  = new ConcurrentHashMap<>();

    // Reverse map: WebSocket → username  (fast lookup on disconnect)
    private final ConcurrentHashMap<WebSocket, String> sessionUsers  = new ConcurrentHashMap<>();

    // ── Registration ──────────────────────────────────────────────────────────

    /**
     * Register a new user and associate them with a WebSocket session.
     *
     * @param username Username chosen by the client.
     * @param session  The WebSocket connection for this user.
     * @return {@code true} if registration succeeded, {@code false} if the
     *         username is already taken.
     */
    public boolean addUser(String username, WebSocket session) {
        // putIfAbsent is atomic – prevents race conditions on simultaneous joins
        if (userSessions.putIfAbsent(username, session) == null) {
            sessionUsers.put(session, username);
            return true;
        }
        return false;
    }

    /**
     * Remove a user by their username.
     *
     * @param username The username to remove.
     */
    public void removeUser(String username) {
        WebSocket session = userSessions.remove(username);
        if (session != null) {
            sessionUsers.remove(session);
        }
    }

    /**
     * Remove a user identified by their WebSocket session (used on disconnect
     * when the username may not be known to the caller).
     *
     * @param session The WebSocket that disconnected.
     * @return The username that was removed, or {@code null} if not found.
     */
    public String removeUserBySession(WebSocket session) {
        String username = sessionUsers.remove(session);
        if (username != null) {
            userSessions.remove(username);
        }
        return username;
    }

    // ── Lookup ────────────────────────────────────────────────────────────────

    /**
     * Check whether a username is already registered.
     *
     * @param username Username to test.
     * @return {@code true} if the username is in use.
     */
    public boolean isUsernameTaken(String username) {
        return userSessions.containsKey(username);
    }

    /**
     * Retrieve the WebSocket session for a given username.
     *
     * @param username The target username.
     * @return The corresponding WebSocket, or {@code null} if not found.
     */
    public WebSocket getUserSession(String username) {
        return userSessions.get(username);
    }

    /**
     * Look up the username associated with a WebSocket session.
     *
     * @param session The WebSocket connection.
     * @return The username, or {@code null} if no mapping exists.
     */
    public String getUsername(WebSocket session) {
        return sessionUsers.get(session);
    }

    // ── Bulk operations ───────────────────────────────────────────────────────

    /**
     * Get an unmodifiable snapshot of all currently online usernames.
     *
     * @return A new {@link List} containing every registered username.
     */
    public List<String> getAllUsers() {
        return Collections.unmodifiableList(new ArrayList<>(userSessions.keySet()));
    }

    /**
     * Get all active WebSocket sessions (e.g. for broadcast).
     *
     * @return A new {@link Collection} of all open sessions.
     */
    public Collection<WebSocket> getAllSessions() {
        return Collections.unmodifiableCollection(userSessions.values());
    }

    /**
     * Return the number of currently connected users.
     */
    public int getUserCount() {
        return userSessions.size();
    }
}
