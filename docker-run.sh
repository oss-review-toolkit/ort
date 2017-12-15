#!/bin/bash -e

DOCKER_IMAGE="oss-review-toolkit:latest"
DOCKER_OPTIONS="--rm"

docker run $DOCKER_OPTIONS $DOCKER_IMAGE "$@"
