#!/bin/bash
# Quick dev script - wrapper for ./gradlew restart
# Usage: ./dev.sh

cd "$(dirname "$0")"
./gradlew restart
