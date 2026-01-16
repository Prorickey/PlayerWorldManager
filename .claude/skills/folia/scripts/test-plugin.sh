#!/bin/bash
# Test plugin loading on Folia server
# Designed for LLM use: starts server, waits for "Done", waits 10s, shuts down, outputs logs
#
# Usage: ./test-plugin.sh [jar-name]
# Example: ./test-plugin.sh folia.jar
#
# Exit codes:
#   0 - Server started and stopped successfully (check output for plugin errors)
#   1 - Server failed to start or crashed
#   2 - Timeout waiting for server to start

set -e

JAR_NAME="${1:-folia.jar}"
MEMORY="2G"
TIMEOUT=120  # seconds to wait for server to start
LOG_FILE="logs/latest.log"
TEST_LOG="test-plugin-output.log"

# Check if JAR exists
if [ ! -f "$JAR_NAME" ]; then
    echo "ERROR: $JAR_NAME not found in $(pwd)"
    echo "Run './gradlew downloadFolia' first to download the server JAR"
    exit 1
fi

# Accept EULA automatically
if [ ! -f "eula.txt" ] || ! grep -q "eula=true" eula.txt; then
    echo "eula=true" > eula.txt
fi

# Create minimal server.properties for fast startup
cat > server.properties << 'EOF'
online-mode=false
spawn-protection=0
max-players=2
level-name=world
gamemode=survival
difficulty=peaceful
allow-nether=false
generate-structures=false
level-type=flat
spawn-monsters=false
spawn-animals=false
# RCON settings
enable-rcon=true
rcon.port=25575
rcon.password=test
EOF

# Clean up old logs
rm -f "$TEST_LOG"
rm -f "logs/latest.log"

echo "=== FOLIA PLUGIN TEST ===" | tee "$TEST_LOG"
echo "Starting Folia server to test plugin loading..." | tee -a "$TEST_LOG"
echo "JAR: $JAR_NAME" | tee -a "$TEST_LOG"
echo "Working directory: $(pwd)" | tee -a "$TEST_LOG"
echo "" | tee -a "$TEST_LOG"

# Create a named pipe for server input
PIPE=$(mktemp -u)
mkfifo "$PIPE"

# Start server with input from pipe, output to both console and log
java -Xms512M -Xmx${MEMORY} \
    -XX:+UseG1GC \
    -jar "$JAR_NAME" --nogui < "$PIPE" 2>&1 | tee -a "$TEST_LOG" &

SERVER_PID=$!

# Open pipe for writing (keep it open)
exec 3>"$PIPE"

# Function to cleanup on exit
cleanup() {
    echo "" | tee -a "$TEST_LOG"
    echo "Cleaning up..." | tee -a "$TEST_LOG"
    exec 3>&-  # Close pipe
    rm -f "$PIPE"
    # Kill server if still running
    if kill -0 $SERVER_PID 2>/dev/null; then
        kill $SERVER_PID 2>/dev/null || true
        wait $SERVER_PID 2>/dev/null || true
    fi
}
trap cleanup EXIT

# Wait for server to print "Done" indicating startup complete
echo "Waiting for server to start (timeout: ${TIMEOUT}s)..." | tee -a "$TEST_LOG"
STARTED=false
ELAPSED=0

while [ $ELAPSED -lt $TIMEOUT ]; do
    if ! kill -0 $SERVER_PID 2>/dev/null; then
        echo "" | tee -a "$TEST_LOG"
        echo "ERROR: Server process died unexpectedly!" | tee -a "$TEST_LOG"
        exit 1
    fi

    # Check if "Done" appears in the log
    if [ -f "$LOG_FILE" ] && grep -q "Done" "$LOG_FILE"; then
        STARTED=true
        break
    fi

    sleep 1
    ELAPSED=$((ELAPSED + 1))
done

if [ "$STARTED" = false ]; then
    echo "" | tee -a "$TEST_LOG"
    echo "ERROR: Timeout waiting for server to start!" | tee -a "$TEST_LOG"
    echo "stop" >&3
    sleep 2
    exit 2
fi

echo "" | tee -a "$TEST_LOG"
echo "Server started! Waiting 10 seconds for plugin initialization..." | tee -a "$TEST_LOG"
sleep 10

# Send stop command
echo "" | tee -a "$TEST_LOG"
echo "Sending stop command..." | tee -a "$TEST_LOG"
echo "stop" >&3

# Wait for server to stop (max 30 seconds)
STOP_TIMEOUT=30
STOP_ELAPSED=0
while kill -0 $SERVER_PID 2>/dev/null && [ $STOP_ELAPSED -lt $STOP_TIMEOUT ]; do
    sleep 1
    STOP_ELAPSED=$((STOP_ELAPSED + 1))
done

# Force kill if still running
if kill -0 $SERVER_PID 2>/dev/null; then
    echo "Server didn't stop gracefully, forcing shutdown..." | tee -a "$TEST_LOG"
    kill -9 $SERVER_PID 2>/dev/null || true
fi

wait $SERVER_PID 2>/dev/null || true

echo "" | tee -a "$TEST_LOG"
echo "=== SERVER STOPPED ===" | tee -a "$TEST_LOG"
echo "" | tee -a "$TEST_LOG"

# Analyze logs for plugin-related messages
echo "=== PLUGIN ANALYSIS ===" | tee -a "$TEST_LOG"
echo "" | tee -a "$TEST_LOG"

if [ -f "$LOG_FILE" ]; then
    # Extract PlayerWorldManager-related lines
    echo "--- PlayerWorldManager Log Lines ---" | tee -a "$TEST_LOG"
    grep -i "PlayerWorldManager\|PWM" "$LOG_FILE" 2>/dev/null | tee -a "$TEST_LOG" || echo "(No PlayerWorldManager messages found)" | tee -a "$TEST_LOG"
    echo "" | tee -a "$TEST_LOG"

    # Check for errors/warnings related to our plugin
    echo "--- Errors and Warnings ---" | tee -a "$TEST_LOG"
    grep -iE "(ERROR|WARN|Exception|Error:)" "$LOG_FILE" 2>/dev/null | grep -v "Can't keep up" | tee -a "$TEST_LOG" || echo "(No errors or warnings found)" | tee -a "$TEST_LOG"
    echo "" | tee -a "$TEST_LOG"

    # Check if plugin loaded successfully
    echo "--- Plugin Load Status ---" | tee -a "$TEST_LOG"
    if grep -q "PlayerWorldManager.*enabled" "$LOG_FILE" 2>/dev/null; then
        echo "SUCCESS: PlayerWorldManager plugin loaded successfully!" | tee -a "$TEST_LOG"
    elif grep -q "PlayerWorldManager.*disabled\|PlayerWorldManager.*failed\|Error loading.*PlayerWorldManager" "$LOG_FILE" 2>/dev/null; then
        echo "FAILURE: PlayerWorldManager plugin failed to load!" | tee -a "$TEST_LOG"
    else
        echo "UNKNOWN: Could not determine plugin load status" | tee -a "$TEST_LOG"
    fi
    echo "" | tee -a "$TEST_LOG"

    # Check if plugin disabled cleanly
    echo "--- Plugin Disable Status ---" | tee -a "$TEST_LOG"
    if grep -q "PlayerWorldManager disabled" "$LOG_FILE" 2>/dev/null; then
        echo "SUCCESS: PlayerWorldManager plugin disabled cleanly!" | tee -a "$TEST_LOG"
    else
        echo "WARNING: Could not confirm clean plugin disable" | tee -a "$TEST_LOG"
    fi
else
    echo "WARNING: Log file not found at $LOG_FILE" | tee -a "$TEST_LOG"
fi

echo "" | tee -a "$TEST_LOG"
echo "=== TEST COMPLETE ===" | tee -a "$TEST_LOG"
echo "Full test output saved to: $TEST_LOG" | tee -a "$TEST_LOG"
echo "Server log available at: $LOG_FILE" | tee -a "$TEST_LOG"
