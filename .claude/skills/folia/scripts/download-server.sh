#!/bin/bash
# Download Folia server JAR from PaperMC API v3
# Documentation: https://docs.papermc.io/misc/downloads-service/
#
# Usage: ./download-server.sh [version] [build]
# Examples:
#   ./download-server.sh 1.21.4           # Latest stable build for 1.21.4
#   ./download-server.sh 1.21.4 123       # Specific build 123
#   ./download-server.sh                  # Latest version, latest stable build

set -e

PROJECT="folia"
API_BASE="https://api.papermc.io/v2"
USER_AGENT="PlayerWorldManager/1.0 (https://github.com/prorickey/PlayerWorldManager)"

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

echo "Fetching Folia information..."

# Get latest version if not specified
if [ -z "$1" ]; then
    PROJECT_INFO=$(curl -sfA "$USER_AGENT" "$API_BASE/projects/$PROJECT")
    if [ -z "$PROJECT_INFO" ]; then
        echo "Error: Could not fetch project info for $PROJECT"
        exit 1
    fi
    VERSION=$(echo "$PROJECT_INFO" | jq -r '.versions[-1]')
    if [ -z "$VERSION" ] || [ "$VERSION" = "null" ]; then
        echo "Error: Could not determine latest version"
        echo "$PROJECT_INFO" | jq '.versions'
        exit 1
    fi
    echo "Using latest version: $VERSION"
else
    VERSION="$1"
fi

# Fetch builds for version
echo "Fetching builds for Folia $VERSION..."
BUILD_DATA=$(curl -sfA "$USER_AGENT" "$API_BASE/projects/$PROJECT/versions/$VERSION/builds")
if [ -z "$BUILD_DATA" ]; then
    echo "Error: No builds found for Folia $VERSION"
    echo "Fetching available versions..."
    curl -sfA "$USER_AGENT" "$API_BASE/projects/$PROJECT" | jq '.versions'
    exit 1
fi

# Get latest build if not specified
if [ -z "$2" ]; then
    BUILD=$(echo "$BUILD_DATA" | jq -r '.builds[-1].build')
    echo "Using latest build: $BUILD"
else
    BUILD="$2"
fi

# Get download filename
BUILD_INFO=$(echo "$BUILD_DATA" | jq -r ".builds[] | select(.build == $BUILD)")
if [ -z "$BUILD_INFO" ] || [ "$BUILD_INFO" = "null" ]; then
    echo "Error: Could not find build $BUILD"
    echo "Available builds:"
    echo "$BUILD_DATA" | jq '[.builds[].build]'
    exit 1
fi

DOWNLOAD_NAME=$(echo "$BUILD_INFO" | jq -r '.downloads.application.name')
CHECKSUM=$(echo "$BUILD_INFO" | jq -r '.downloads.application.sha256')
DOWNLOAD_URL="$API_BASE/projects/$PROJECT/versions/$VERSION/builds/$BUILD/downloads/$DOWNLOAD_NAME"

echo ""
echo "Download details:"
echo "  File: $DOWNLOAD_NAME"
echo "  SHA256: ${CHECKSUM:0:16}..."
echo "  URL: $DOWNLOAD_URL"
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
