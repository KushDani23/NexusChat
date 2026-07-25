@echo off
REM ============================================================
REM  build.bat  –  Download dependencies, compile & run ChatApp
REM  Usage: build.bat
REM ============================================================

setlocal EnableDelayedExpansion

SET PROJECT_ROOT=%~dp0
SET LIB_DIR=%PROJECT_ROOT%lib
SET SRC_DIR=%PROJECT_ROOT%src
SET OUT_DIR=%PROJECT_ROOT%out
SET LOG_DIR=%PROJECT_ROOT%logs

echo ============================================================
echo   ChatApp Build Script
echo ============================================================

REM Create required directories
if not exist "%LIB_DIR%"  mkdir "%LIB_DIR%"
if not exist "%OUT_DIR%"  mkdir "%OUT_DIR%"
if not exist "%LOG_DIR%"  mkdir "%LOG_DIR%"

REM ── Download Java-WebSocket library ────────────────────────
SET WS_JAR=%LIB_DIR%\java-websocket-1.5.3.jar
SET WS_URL=https://repo1.maven.org/maven2/org/java-websocket/Java-WebSocket/1.5.3/Java-WebSocket-1.5.3.jar

if not exist "%WS_JAR%" (
    echo [INFO] Downloading Java-WebSocket library...
    powershell -Command "Invoke-WebRequest -Uri '%WS_URL%' -OutFile '%WS_JAR%' -UseBasicParsing"
    if errorlevel 1 (
        echo [ERROR] Failed to download Java-WebSocket. Check your internet connection.
        exit /b 1
    )
    echo [INFO] Java-WebSocket downloaded successfully.
) else (
    echo [INFO] Java-WebSocket library already present.
)

REM ── Download SLF4J (required by Java-WebSocket) ─────────────
SET SLF4J_JAR=%LIB_DIR%\slf4j-simple-1.7.36.jar
SET SLF4J_URL=https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/1.7.36/slf4j-simple-1.7.36.jar
SET SLF4J_API_JAR=%LIB_DIR%\slf4j-api-1.7.36.jar
SET SLF4J_API_URL=https://repo1.maven.org/maven2/org/slf4j/slf4j-api/1.7.36/slf4j-api-1.7.36.jar

if not exist "%SLF4J_API_JAR%" (
    echo [INFO] Downloading SLF4J API...
    powershell -Command "Invoke-WebRequest -Uri '%SLF4J_API_URL%' -OutFile '%SLF4J_API_JAR%' -UseBasicParsing"
)

if not exist "%SLF4J_JAR%" (
    echo [INFO] Downloading SLF4J Simple...
    powershell -Command "Invoke-WebRequest -Uri '%SLF4J_URL%' -OutFile '%SLF4J_JAR%' -UseBasicParsing"
)

REM ── Compile ─────────────────────────────────────────────────
echo.
echo [INFO] Compiling Java sources...

SET CLASSPATH=%LIB_DIR%\java-websocket-1.5.3.jar;%LIB_DIR%\slf4j-api-1.7.36.jar;%LIB_DIR%\slf4j-simple-1.7.36.jar

javac -cp "%CLASSPATH%" -d "%OUT_DIR%" -sourcepath "%SRC_DIR%" ^
    "%SRC_DIR%\config\ServerConfig.java" ^
    "%SRC_DIR%\model\MessageType.java" ^
    "%SRC_DIR%\model\ChatMessage.java" ^
    "%SRC_DIR%\server\ChatLogger.java" ^
    "%SRC_DIR%\server\UserManager.java" ^
    "%SRC_DIR%\server\WebSocketHandler.java" ^
    "%SRC_DIR%\server\ChatServer.java"

if errorlevel 1 (
    echo [ERROR] Compilation failed.
    exit /b 1
)

echo [INFO] Compilation successful.

REM ── Run ─────────────────────────────────────────────────────
echo.
echo [INFO] Starting ChatApp server...
echo [INFO] Open your browser at: http://localhost:8080
echo [INFO] Press Ctrl+C to stop.
echo.

java -cp "%OUT_DIR%;%CLASSPATH%" server.ChatServer

endlocal
