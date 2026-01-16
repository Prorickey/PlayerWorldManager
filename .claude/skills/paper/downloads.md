# Downloading Paper Server JARs

PaperMC provides REST and GraphQL APIs to download Paper server JARs.

**Important**: All requests must include a valid `User-Agent` header with contact information.

## Download Script

Save as `scripts/download-server.sh`:

```bash
#!/bin/bash
# Download Paper or Folia server JAR from PaperMC API v3
# Usage: ./download-server.sh [paper|folia] [version] [build]
# Examples:
#   ./download-server.sh paper 1.21.4           # Latest stable build for 1.21.4
#   ./download-server.sh folia 1.21.4           # Latest Folia build for 1.21.4
#   ./download-server.sh paper 1.21.4 123       # Specific build 123
#   ./download-server.sh paper                  # Latest version, latest stable build

set -e

PROJECT="${1:-paper}"
API_BASE="https://fill.papermc.io/v3"
USER_AGENT="MyPlugin/1.0 (https://github.com/username/repo)"

# Get latest version if not specified
if [ -z "$2" ]; then
    # Versions are grouped by major version; get highest group, then first version in it
    VERSION=$(curl -sA "$USER_AGENT" "$API_BASE/projects/$PROJECT" | jq -r '
        .versions | to_entries |
        sort_by(.key | split(".") | map(tonumber? // 999)) |
        last | .value[0]')
    echo "Using latest version: $VERSION"
else
    VERSION="$2"
fi

# Get latest stable build if not specified
if [ -z "$3" ]; then
    BUILD_DATA=$(curl -sA "$USER_AGENT" "$API_BASE/projects/$PROJECT/versions/$VERSION/builds")
    # Prefer STABLE channel, fall back to any build
    BUILD=$(echo "$BUILD_DATA" | jq -r '
        [.[] | select(.channel == "STABLE")] | last // (. | last) | .id')
    echo "Using build: $BUILD"
else
    BUILD="$3"
fi

# Get download info
DOWNLOAD_INFO=$(curl -sA "$USER_AGENT" "$API_BASE/projects/$PROJECT/versions/$VERSION/builds" | \
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

## REST API Reference

Base URL: `https://fill.papermc.io/v3`

### Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /projects/{project}` | Get project info with versions grouped |
| `GET /projects/{project}/versions/{version}/builds` | List all builds for a version |

### Build Response Fields

```json
{
  "id": 123,
  "channel": "STABLE",
  "downloads": {
    "server:default": {
      "name": "paper-1.21.4-123.jar",
      "url": "https://...",
      "checksums": {
        "sha256": "abc123..."
      },
      "size": 12345678
    }
  }
}
```

- `id` - Build number
- `channel` - `STABLE` or `EXPERIMENTAL` (use STABLE for production)
- `downloads["server:default"]` - Server JAR download info

## GraphQL API

For more complex queries, use the GraphQL API at `https://fill.papermc.io/graphql`.

### Get Latest Stable Build

```graphql
query {
  project(key: "paper") {
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
                  size
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

### Using curl with GraphQL

```bash
curl -X POST https://fill.papermc.io/graphql \
  -H "Content-Type: application/json" \
  -H "User-Agent: MyApp/1.0 (contact@example.com)" \
  -d '{"query": "{ project(key: \"paper\") { versions(first: 1, orderBy: {direction: DESC}) { edges { node { key } } } } }"}'
```

### GraphQL Playground

Interactive query builder: `https://fill.papermc.io/graphiql?path=/graphql`

## Setting Up a Test Server

```bash
# Create server directory
mkdir -p paper-test && cd paper-test

# Download latest Paper
../scripts/download-server.sh paper

# Accept EULA
echo "eula=true" > eula.txt

# Start server (first run generates configs)
java -Xmx2G -jar paper-*.jar --nogui

# Copy your plugin to plugins/ folder
cp ../build/libs/MyPlugin-*.jar plugins/
```

## Resources

- [PaperMC Downloads API Documentation](https://docs.papermc.io/misc/downloads-service/)
- [PaperMC Downloads Page](https://papermc.io/downloads)
