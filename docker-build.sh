#!/bin/bash -e

DOCKER_IMAGE="oss-review-toolkit:latest"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

"$SCRIPT_DIR/gradlew" distTar
docker build -t "$DOCKER_IMAGE" "$SCRIPT_DIR"
