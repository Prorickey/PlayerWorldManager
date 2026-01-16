#!/bin/bash
# Download Paper or Folia server JAR from PaperMC API v3
# Documentation: https://docs.papermc.io/misc/downloads-service/
#
# Usage: ./download-server.sh [paper|folia] [version] [build]
# Examples:
#   ./download-server.sh paper 1.21.4           # Latest stable build for 1.21.4
#   ./download-server.sh folia 1.21.4           # Latest Folia build for 1.21.4
#   ./download-server.sh paper 1.21.4 123       # Specific build 123
#   ./download-server.sh paper                  # Latest version, latest stable build
#   ./download-server.sh folia                  # Latest Folia version, latest stable build

set -e

PROJECT="${1:-paper}"
API_BASE="https://fill.papermc.io/v3"
# User-Agent is required by PaperMC API - include contact info
USER_AGENT="PlayerWorldManager/1.0 (https://github.com/prorickey/PlayerWorldManager)"

# Validate project
if [[ "$PROJECT" != "paper" && "$PROJECT" != "folia" && "$PROJECT" != "velocity" ]]; then
    echo "Error: Project must be 'paper', 'folia', or 'velocity'"
    exit 1
fi

# Check for required tools
if ! command -v jq &> /dev/null; then
    echo "Error: jq is required but not installed."
    echo "Install with: brew install jq (macOS) or apt install jq (Linux)"
    exit 1
fi

if ! command -v curl &> /dev/null; then
    echo "Error: curl is required but not installed."
    exit 1
fi

echo "Fetching $PROJECT information..."

# Get latest version if not specified
if [ -z "$2" ]; then
    PROJECT_INFO=$(curl -sfA "$USER_AGENT" "$API_BASE/projects/$PROJECT")
    if [ -z "$PROJECT_INFO" ]; then
        echo "Error: Could not fetch project info for $PROJECT"
        exit 1
    fi
    # Versions are grouped by major version (e.g., "1.21": ["1.21.11", "1.21.4", ...])
    # Get the highest major version group, then the first (latest) version in that group
    VERSION=$(echo "$PROJECT_INFO" | jq -r '
        .versions | to_entries |
        sort_by(.key | split(".") | map(tonumber? // 999)) |
        last | .value[0]')
    if [ -z "$VERSION" ] || [ "$VERSION" = "null" ]; then
        echo "Error: Could not determine latest version"
        echo "Available version groups:"
        echo "$PROJECT_INFO" | jq '.versions | keys'
        exit 1
    fi
    echo "Using latest version: $VERSION"
else
    VERSION="$2"
fi

# Fetch builds for version
echo "Fetching builds for $PROJECT $VERSION..."
BUILD_DATA=$(curl -sfA "$USER_AGENT" "$API_BASE/projects/$PROJECT/versions/$VERSION/builds")
if [ -z "$BUILD_DATA" ] || [ "$BUILD_DATA" = "[]" ]; then
    echo "Error: No builds found for $PROJECT $VERSION"
    echo "Fetching available versions..."
    curl -sfA "$USER_AGENT" "$API_BASE/projects/$PROJECT" | jq '.versions'
    exit 1
fi

# Get latest stable build if not specified
if [ -z "$3" ]; then
    # Prefer STABLE channel, fall back to any build if no stable builds exist
    BUILD=$(echo "$BUILD_DATA" | jq -r '
        ([.[] | select(.channel == "STABLE")] | last // (. | last)) | .id')
    CHANNEL=$(echo "$BUILD_DATA" | jq -r "
        .[] | select(.id == $BUILD) | .channel")
    echo "Using build: $BUILD ($CHANNEL)"
else
    BUILD="$3"
fi

# Get download info for the specific build
DOWNLOAD_INFO=$(echo "$BUILD_DATA" | jq -r ".[] | select(.id == $BUILD) | .downloads[\"server:default\"]")
if [ -z "$DOWNLOAD_INFO" ] || [ "$DOWNLOAD_INFO" = "null" ]; then
    echo "Error: Could not find download info for build $BUILD"
    echo "Available builds:"
    echo "$BUILD_DATA" | jq '[.[] | {id, channel}]'
    exit 1
fi

DOWNLOAD_NAME=$(echo "$DOWNLOAD_INFO" | jq -r '.name')
DOWNLOAD_URL=$(echo "$DOWNLOAD_INFO" | jq -r '.url')
CHECKSUM=$(echo "$DOWNLOAD_INFO" | jq -r '.checksums.sha256')
FILE_SIZE=$(echo "$DOWNLOAD_INFO" | jq -r '.size')

echo ""
echo "Download details:"
echo "  File: $DOWNLOAD_NAME"
echo "  Size: $((FILE_SIZE / 1024 / 1024)) MB"
echo "  SHA256: ${CHECKSUM:0:16}..."
echo ""

# Download the JAR
echo "Downloading $DOWNLOAD_NAME..."
curl -A "$USER_AGENT" -# -o "$DOWNLOAD_NAME" -L "$DOWNLOAD_URL"

# Verify checksum
echo ""
echo "Verifying checksum..."
if command -v sha256sum &> /dev/null; then
    echo "$CHECKSUM  $DOWNLOAD_NAME" | sha256sum -c -
elif command -v shasum &> /dev/null; then
    echo "$CHECKSUM  $DOWNLOAD_NAME" | shasum -a 256 -c -
else
    echo "Warning: No sha256sum or shasum available, skipping verification"
fi

echo ""
echo "Successfully downloaded: $DOWNLOAD_NAME"
echo ""
echo "To start the server:"
echo "  echo 'eula=true' > eula.txt"
echo "  java -Xmx2G -jar $DOWNLOAD_NAME --nogui"
