/**
 * websocket.js
 * WebSocketClient - manages the WebSocket lifecycle, auto-reconnect,
 * message sending/receiving, and dispatches parsed events to the app layer.
 */
const WebSocketClient = (() => {

    const WS_URL = `ws://${location.hostname}:8081/chat`;
    const MAX_RECONNECT_ATTEMPTS = 5;
    const RECONNECT_DELAY_MS     = 2000;

    let socket             = null;
    let username           = null;
    let reconnectAttempts  = 0;
    let reconnectTimer     = null;
    let intentionalClose   = false;

    // ── Callbacks set by the app layer ────────────────────────────────────────
    let onOpen        = () => {};
    let onClose       = () => {};
    let onMessage     = () => {};
    let onReconnecting = () => {};

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Open a WebSocket connection and send REGISTER once connected.
     * @param {string}   name   The username to register.
     * @param {Object}   cbs    Callback object: { onOpen, onClose, onMessage, onReconnecting }
     */
    function connect(name, cbs = {}) {
        username      = name;
        intentionalClose = false;
        if (cbs.onOpen)        onOpen        = cbs.onOpen;
        if (cbs.onClose)       onClose       = cbs.onClose;
        if (cbs.onMessage)     onMessage     = cbs.onMessage;
        if (cbs.onReconnecting) onReconnecting = cbs.onReconnecting;

        openSocket();
    }

    /** Close the connection intentionally (e.g. logout). */
    function disconnect() {
        intentionalClose = true;
        clearTimeout(reconnectTimer);
        if (socket) socket.close();
    }

    /**
     * Send a JSON-serialisable object to the server.
     * @param {Object} data
     */
    function send(data) {
        if (socket && socket.readyState === WebSocket.OPEN) {
            socket.send(JSON.stringify(data));
        } else {
            console.warn('[WS] Cannot send – socket not open', data);
        }
    }

    /** Return true if the socket is currently connected. */
    function isConnected() {
        return socket && socket.readyState === WebSocket.OPEN;
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    function openSocket() {
        if (socket) {
            socket.onopen = socket.onclose = socket.onmessage = socket.onerror = null;
            try { socket.close(); } catch (_) {}
        }

        console.log('[WS] Connecting to', WS_URL);
        socket = new WebSocket(WS_URL);

        socket.onopen = () => {
            console.log('[WS] Connected');
            reconnectAttempts = 0;
            // Register the user immediately
            send({ type: 'REGISTER', username });
        };

        socket.onmessage = (event) => {
            try {
                const data = JSON.parse(event.data);
                onMessage(data);
            } catch (e) {
                console.error('[WS] Failed to parse message:', event.data, e);
            }
        };

        socket.onclose = (event) => {
            console.log('[WS] Connection closed', event.code, event.reason);
            if (!intentionalClose) {
                scheduleReconnect();
            } else {
                onClose();
            }
        };

        socket.onerror = (err) => {
            console.error('[WS] Error:', err);
        };
    }

    function scheduleReconnect() {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            console.warn('[WS] Max reconnect attempts reached.');
            onClose();
            return;
        }
        reconnectAttempts++;
        const delay = RECONNECT_DELAY_MS * reconnectAttempts;
        console.log(`[WS] Reconnecting in ${delay}ms (attempt ${reconnectAttempts})`);
        onReconnecting(reconnectAttempts, MAX_RECONNECT_ATTEMPTS);
        reconnectTimer = setTimeout(openSocket, delay);
    }

    return { connect, disconnect, send, isConnected };
})();
