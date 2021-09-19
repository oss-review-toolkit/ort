#!/usr/bin/env bash

set -euo pipefail

tag=ort/with_opossum

buildDockerImg() {
    DOCKER_BUILDKIT=1 docker build "$(dirname "$0")" --tag $tag --network=host
}

reportAsOpossum() {
    inputFile="$(readlink -f $1)"
    inputDir="$(dirname "$inputFile")"

    mkdir -p "$HOME/.ort/dockerHome/"

    local dockerArgs=("run" "-i" "--rm")
    dockerArgs+=("-v" "/etc/group:/etc/group:ro")
    dockerArgs+=("-v" "/etc/passwd:/etc/passwd:ro")
    dockerArgs+=("-v" "$HOME/.ort/dockerHome:$HOME")
    dockerArgs+=("-u" "$(id -u):$(id -g)")
    dockerArgs+=("-v" "$inputDir:$inputDir" "-w" "$inputDir")
    dockerArgs+=("--net=host")
    dockerArgs+=("${tag}:latest")
    dockerArgs+=("--force-overwrite" "--performance")

    set -x
    docker "${dockerArgs[@]}" \
        report -f Opossum \
        -i "$inputFile" \
        -o "$inputDir"
}

buildDockerImg

for ortFile in "$@"; do
    reportAsOpossum "$ortFile"
done
