# NexusChat | Real-Time Web Chat Application
### WhatsApp / Telegram Style · Java WebSocket Backend · Vanilla JS Frontend

---

## Overview

A modern, full-featured real-time chat application built with **Core Java** (backend) and **HTML + CSS + JavaScript** (frontend). It features:

- **Group Chat** — broadcast messages to everyone online
- **Private Messaging** — one-on-one conversations with any online user
- **Read Receipts** — ✓ (sent) → ✓✓ (delivered) → 🔵✓✓ (read)
- **Typing Indicators** — animated three-dot "is typing…" display
- **Online Users Sidebar** — live-updated with avatars and status dots
- **Dark / Light Mode** — instant toggle with localStorage persistence
- **Auto-Reconnect** — up to 5 attempts with exponential back-off
- **Server-side Logging** — all events written to `logs/chat_history.log`

---

## Folder Structure

```
ChatApplication/
├── src/
│   ├── config/
│   │   └── ServerConfig.java        # Port numbers, paths, limits
│   ├── model/
│   │   ├── MessageType.java         # Enum for all message types
│   │   └── ChatMessage.java         # Message value object (UUID, status)
│   ├── server/
│   │   ├── ChatServer.java          # Entry point: HTTP + WebSocket servers
│   │   ├── WebSocketHandler.java    # Routes messages, handles lifecycle
│   │   ├── UserManager.java         # Thread-safe user registry
│   │   └── ChatLogger.java          # File-based activity logger
│   └── webapp/                      # Static frontend assets
│       ├── index.html
│       ├── css/
│       │   ├── light-theme.css
│       │   ├── dark-theme.css
│       │   └── style.css
│       └── js/
│           ├── theme-manager.js
│           ├── read-receipts.js
│           ├── websocket.js
│           └── app.js
├── lib/                             # JARs (auto-downloaded by build script)
├── out/                             # Compiled .class files
├── logs/
│   └── chat_history.log
├── build.bat                        # Windows build & run script
├── build.sh                         # Linux / macOS build & run script
└── README.md
```

---

## Prerequisites

| Requirement | Version |
|-------------|---------|
| Java JDK    | 8 or higher |
| Internet    | Required first run (to download JARs) |
| Browser     | Any modern browser with WebSocket support |

---

## Quick Start

### Windows
```bat
build.bat
```

### Linux / macOS
```bash
chmod +x build.sh
./build.sh
```

The build script will:
1. Create `lib/`, `out/`, `logs/` directories
2. Download `Java-WebSocket-1.5.3.jar` + SLF4J from Maven Central
3. Compile all Java sources into `out/`
4. Start the server

Then open **http://localhost:8080** in your browser.

---

## Manual Build

```bash
# Compile
javac -cp "lib/*" -d out -sourcepath src \
    src/config/ServerConfig.java \
    src/model/MessageType.java \
    src/model/ChatMessage.java \
    src/server/ChatLogger.java \
    src/server/UserManager.java \
    src/server/WebSocketHandler.java \
    src/server/ChatServer.java

# Run
java -cp "out;lib/*" server.ChatServer   # Windows
java -cp "out:lib/*" server.ChatServer   # Linux / macOS
```

---

## Ports

| Server    | Port | URL                         |
|-----------|------|-----------------------------|
| HTTP      | 8080 | http://localhost:8080        |
| WebSocket | 8081 | ws://localhost:8081/chat     |

---

## Features In Detail

### Read Receipts
| Status    | Icon    | When |
|-----------|---------|------|
| Sending   | ⏳      | Immediately after pressing Send |
| Sent      | ✓ gray  | Server confirms receipt |
| Delivered | ✓✓ gray | Target client receives message |
| Read      | ✓✓ blue | Target opens the conversation |

### Dark / Light Mode
- Toggle with the 🌙☀️ slider in the sidebar header
- Preference saved to `localStorage`
- First visit auto-detects OS preference (`prefers-color-scheme`)

### Typing Indicator
- Shows "**[User]** is typing …" with a bouncing-dot animation
- Auto-disappears 4 seconds after the last keystroke
- Works in both group and private chats

### Message Protocol (JSON over WebSocket)

**Client → Server**
```json
{ "type": "REGISTER",     "username": "Alice" }
{ "type": "GROUP",        "content": "Hello everyone!" }
{ "type": "PRIVATE",      "target": "Bob", "content": "Hi!" }
{ "type": "TYPING",       "target": "Bob", "isTyping": true }
{ "type": "READ_RECEIPT", "messageId": "msg_abc123", "sender": "Bob" }
```

**Server → Client**
```json
{ "type": "REGISTER_RESPONSE", "success": true, "username": "Alice" }
{ "type": "GROUP",   "messageId": "msg_abc", "sender": "Alice", "content": "Hello", "timestamp": "10:30 AM", "status": "sent" }
{ "type": "PRIVATE", "messageId": "msg_xyz", "sender": "Alice", "target": "Bob",   "content": "Hi!", "timestamp": "10:31 AM", "status": "sent" }
{ "type": "MESSAGE_STATUS", "messageId": "msg_abc", "status": "read" }
{ "type": "USERS_LIST",  "users": ["Alice","Bob","Charlie"] }
{ "type": "USER_JOINED", "username": "Charlie" }
{ "type": "USER_LEFT",   "username": "Charlie" }
{ "type": "TYPING",      "sender": "Bob", "isTyping": true }
```

---

## Log Format

```
2026-07-18 10:30:15 - [INFO] User 'Alice' joined the chat
2026-07-18 10:30:25 - [GROUP] Alice: Hello everyone!
2026-07-18 10:30:30 - [PRIVATE] Alice -> Bob: Hey Bob!
2026-07-18 10:30:35 - [TYPING] Bob is typing to Alice...
2026-07-18 10:30:45 - [READ] Bob read message msg_abc123
2026-07-18 10:30:46 - [STATUS] Message msg_abc123 marked as read
2026-07-18 10:30:50 - [INFO] User 'Bob' left the chat
```

---

## Architecture

```
Browser (Port 8080)               Server (Java)
┌─────────────────┐               ┌─────────────────────────────┐
│   index.html    │  HTTP/8080    │  ChatServer (main)          │
│   style.css     │◄─────────────►│  └─ HttpServer (static)    │
│   app.js        │               │                             │
│   websocket.js  │  WS/8081      │  WebSocketHandler           │
│   theme-mgr.js  │◄─────────────►│  ├─ UserManager            │
│   read-rcpts.js │               │  └─ ChatLogger              │
└─────────────────┘               └─────────────────────────────┘
```

---

## Testing Multi-User

1. Open **http://localhost:8080** in **Tab 1** → login as `Alice`
2. Open **http://localhost:8080** in **Tab 2** → login as `Bob`
3. Send group messages — both tabs receive them
4. Click on `Bob` in Alice's sidebar → start a private conversation
5. Watch ✓ turn into ✓✓ when Bob receives, then blue ✓✓ when Bob opens the chat

---

## Technologies

| Layer    | Technology |
|----------|------------|
| Backend  | Core Java 8+, Java-WebSocket 1.5.3, com.sun.net.httpserver |
| Frontend | HTML5, CSS3 (CSS Variables), Vanilla JavaScript (ES6+) |
| Protocol | WebSocket (RFC 6455), JSON |
| Styling  | Inter font (Google Fonts), CSS custom properties |
| Logging  | Java java.nio.file (file append) |

---
