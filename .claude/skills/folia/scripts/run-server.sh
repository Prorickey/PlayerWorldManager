#!/bin/bash
# Run Folia server interactively
# This script is designed to be run from the server directory (e.g., run/)
#
# Usage: ./run-server.sh [jar-name] [memory]
# Examples:
#   ./run-server.sh                    # Use folia.jar with 2G memory
#   ./run-server.sh folia.jar 4G       # Use folia.jar with 4G memory

set -e

JAR_NAME="${1:-folia.jar}"
MEMORY="${2:-2G}"

# Check if JAR exists
if [ ! -f "$JAR_NAME" ]; then
    echo "Error: $JAR_NAME not found in $(pwd)"
    echo "Run './gradlew downloadFolia' first to download the server JAR"
    exit 1
fi

# Accept EULA automatically
if [ ! -f "eula.txt" ] || ! grep -q "eula=true" eula.txt; then
    echo "Accepting EULA..."
    echo "eula=true" > eula.txt
fi

# Create server.properties with sane defaults if it doesn't exist
if [ ! -f "server.properties" ]; then
    echo "Creating default server.properties..."
    cat > server.properties << 'EOF'
# Folia server properties
online-mode=false
spawn-protection=0
max-players=20
level-name=world
gamemode=survival
difficulty=normal
allow-nether=true
enable-command-block=true
# RCON settings for remote command execution
enable-rcon=true
rcon.port=25575
rcon.password=test
EOF
fi

# Ensure RCON is enabled (update existing server.properties if needed)
if ! grep -q "enable-rcon=true" server.properties 2>/dev/null; then
    echo "" >> server.properties
    echo "# RCON settings for remote command execution" >> server.properties
    echo "enable-rcon=true" >> server.properties
    echo "rcon.port=25575" >> server.properties
    echo "rcon.password=test" >> server.properties
fi

echo ""
echo "Starting Folia server..."
echo "  JAR: $JAR_NAME"
echo "  Memory: $MEMORY"
echo "  Working directory: $(pwd)"
echo ""
echo "Type 'stop' to gracefully shut down the server."
echo ""

# Run the server
exec java -Xms512M -Xmx${MEMORY} \
    -XX:+UseG1GC \
    -XX:+ParallelRefProcEnabled \
    -XX:MaxGCPauseMillis=200 \
    -XX:+UnlockExperimentalVMOptions \
    -XX:+DisableExplicitGC \
    -XX:+AlwaysPreTouch \
    -XX:G1NewSizePercent=30 \
    -XX:G1MaxNewSizePercent=40 \
    -XX:G1HeapRegionSize=8M \
    -XX:G1ReservePercent=20 \
    -XX:G1HeapWastePercent=5 \
    -XX:G1MixedGCCountTarget=4 \
    -XX:InitiatingHeapOccupancyPercent=15 \
    -XX:G1MixedGCLiveThresholdPercent=90 \
    -XX:G1RSetUpdatingPauseTimePercent=5 \
    -XX:SurvivorRatio=32 \
    -XX:+PerfDisableSharedMem \
    -XX:MaxTenuringThreshold=1 \
    -jar "$JAR_NAME" --nogui
