# Downloading Folia Server JARs

Folia server JARs are available from PaperMC's API, similar to Paper.

**Important**: All requests must include a valid `User-Agent` header with contact information.

## Gradle Tasks (Recommended)

```bash
# Download Folia server JAR to run/ directory
./gradlew downloadFolia

# Build plugin and deploy to run/plugins/
./gradlew deployPlugin

# Run Folia server interactively
./gradlew runFolia

# Test plugin loading (for LLM use - auto start/stop)
./gradlew testPlugin
```

## Download Script

Use the script at `.claude/skills/folia/scripts/download-server.sh`:

```bash
# Download latest Folia
./.claude/skills/folia/scripts/download-server.sh

# Download specific version
./.claude/skills/folia/scripts/download-server.sh 1.21.4

# Download specific build
./.claude/skills/folia/scripts/download-server.sh 1.21.4 15
```

Or use this inline script:

```bash
#!/bin/bash
# Download Folia server JAR from PaperMC API v3
# Usage: ./download-folia.sh [version] [build]

set -e

API_BASE="https://fill.papermc.io/v3"
USER_AGENT="MyPlugin/1.0 (https://github.com/username/repo)"

# Get latest version if not specified
if [ -z "$1" ]; then
    VERSION=$(curl -sA "$USER_AGENT" "$API_BASE/projects/folia" | jq -r '
        .versions | to_entries |
        sort_by(.key | split(".") | map(tonumber? // 999)) |
        last | .value[0]')
    echo "Using latest version: $VERSION"
else
    VERSION="$1"
fi

# Get latest stable build if not specified
if [ -z "$2" ]; then
    BUILD_DATA=$(curl -sA "$USER_AGENT" "$API_BASE/projects/folia/versions/$VERSION/builds")
    BUILD=$(echo "$BUILD_DATA" | jq -r '
        [.[] | select(.channel == "STABLE")] | last // (. | last) | .id')
    echo "Using build: $BUILD"
else
    BUILD="$2"
fi

# Get download info
DOWNLOAD_INFO=$(curl -sA "$USER_AGENT" "$API_BASE/projects/folia/versions/$VERSION/builds" | \
    jq -r ".[] | select(.id == $BUILD) | .downloads[\"server:default\"]")

DOWNLOAD_NAME=$(echo "$DOWNLOAD_INFO" | jq -r '.name')
DOWNLOAD_URL=$(echo "$DOWNLOAD_INFO" | jq -r '.url')
CHECKSUM=$(echo "$DOWNLOAD_INFO" | jq -r '.checksums.sha256')

# Download the JAR
echo "Downloading $DOWNLOAD_NAME..."
curl -A "$USER_AGENT" -# -o "$DOWNLOAD_NAME" -L "$DOWNLOAD_URL"

# Verify checksum
echo "Verifying checksum..."
if command -v sha256sum &> /dev/null; then
    echo "$CHECKSUM  $DOWNLOAD_NAME" | sha256sum -c -
elif command -v shasum &> /dev/null; then
    echo "$CHECKSUM  $DOWNLOAD_NAME" | shasum -a 256 -c -
fi

echo "Downloaded: $DOWNLOAD_NAME"
```

## GraphQL Query for Folia

```graphql
query {
  project(key: "folia") {
    versions(first: 1, orderBy: {direction: DESC}) {
      edges {
        node {
          key
          builds(filterBy: {channel: STABLE}, first: 1, orderBy: {direction: DESC}) {
            edges {
              node {
                number
                download(key: "server:default") {
                  name
                  url
                  checksums { sha256 }
                }
              }
            }
          }
        }
      }
    }
  }
}
```

Interactive playground: `https://fill.papermc.io/graphiql?path=/graphql`

## Setting Up a Folia Test Server

Using gradle (recommended):

```bash
# Download Folia and deploy plugin
./gradlew downloadFolia deployPlugin

# Run interactively
./gradlew runFolia
```

Manual setup:

```bash
# Create server directory
mkdir -p run && cd run

# Download Folia
../.claude/skills/folia/scripts/download-server.sh 1.21.4
mv folia-*.jar folia.jar

# Accept EULA
echo "eula=true" > eula.txt

# Copy your plugin
mkdir -p plugins
cp ../build/libs/PlayerWorldManager-*.jar plugins/

# Start server
java -Xmx2G -jar folia.jar --nogui
```

## Testing Tips

1. **Test with multiple players** - Folia's threading model requires testing interactions between players in different regions

2. **Test teleportation** - Cross-region teleports are the most common source of bugs

3. **Watch for errors** - Folia will log errors when plugins access entities from wrong threads

4. **Test async operations** - Ensure database queries and HTTP requests work correctly

## Differences from Paper Testing

| Aspect | Paper | Folia |
|--------|-------|-------|
| Test server | Paper scripts | `./gradlew runFolia` |
| Plugin test | Manual | `./gradlew testPlugin` |
| Thread model | Single main thread | Multiple region threads |
| Error visibility | Runtime exceptions | Often silent failures |
| Player count needed | 1 | 2+ for region testing |

## Resources

- [Folia GitHub](https://github.com/PaperMC/Folia)
- [PaperMC Downloads API](https://docs.papermc.io/misc/downloads-service/)
