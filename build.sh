#!/bin/bash
# ============================================================
#  build.sh  –  Linux/macOS: Download dependencies, compile & run ChatApp
#  Usage: chmod +x build.sh && ./build.sh
# ============================================================

PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
LIB_DIR="$PROJECT_ROOT/lib"
SRC_DIR="$PROJECT_ROOT/src"
OUT_DIR="$PROJECT_ROOT/out"
LOG_DIR="$PROJECT_ROOT/logs"

echo "============================================================"
echo "  ChatApp Build Script"
echo "============================================================"

mkdir -p "$LIB_DIR" "$OUT_DIR" "$LOG_DIR"

# ── Download Java-WebSocket library ────────────────────────────
WS_JAR="$LIB_DIR/java-websocket-1.5.3.jar"
WS_URL="https://repo1.maven.org/maven2/org/java-websocket/Java-WebSocket/1.5.3/Java-WebSocket-1.5.3.jar"

if [ ! -f "$WS_JAR" ]; then
    echo "[INFO] Downloading Java-WebSocket..."
    curl -L -o "$WS_JAR" "$WS_URL" || { echo "[ERROR] Download failed."; exit 1; }
else
    echo "[INFO] Java-WebSocket already present."
fi

# ── Download SLF4J ─────────────────────────────────────────────
SLF4J_API_JAR="$LIB_DIR/slf4j-api-1.7.36.jar"
SLF4J_JAR="$LIB_DIR/slf4j-simple-1.7.36.jar"

[ ! -f "$SLF4J_API_JAR" ] && curl -L -o "$SLF4J_API_JAR" \
    "https://repo1.maven.org/maven2/org/slf4j/slf4j-api/1.7.36/slf4j-api-1.7.36.jar"

[ ! -f "$SLF4J_JAR" ] && curl -L -o "$SLF4J_JAR" \
    "https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/1.7.36/slf4j-simple-1.7.36.jar"

# ── Compile ────────────────────────────────────────────────────
echo ""
echo "[INFO] Compiling Java sources..."

CLASSPATH="$LIB_DIR/java-websocket-1.5.3.jar:$LIB_DIR/slf4j-api-1.7.36.jar:$LIB_DIR/slf4j-simple-1.7.36.jar"

javac -cp "$CLASSPATH" -d "$OUT_DIR" -sourcepath "$SRC_DIR" \
    "$SRC_DIR/config/ServerConfig.java" \
    "$SRC_DIR/model/MessageType.java" \
    "$SRC_DIR/model/ChatMessage.java" \
    "$SRC_DIR/server/ChatLogger.java" \
    "$SRC_DIR/server/UserManager.java" \
    "$SRC_DIR/server/WebSocketHandler.java" \
    "$SRC_DIR/server/ChatServer.java"

if [ $? -ne 0 ]; then
    echo "[ERROR] Compilation failed."
    exit 1
fi

echo "[INFO] Compilation successful."

# ── Run ────────────────────────────────────────────────────────
echo ""
echo "[INFO] Starting ChatApp server..."
echo "[INFO] Open your browser at: http://localhost:8080"
echo "[INFO] Press Ctrl+C to stop."
echo ""

java -cp "$OUT_DIR:$CLASSPATH" server.ChatServer
