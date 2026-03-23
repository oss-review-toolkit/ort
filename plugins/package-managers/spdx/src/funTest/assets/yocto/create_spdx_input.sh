#!/usr/bin/env bash

YOCTO_BRANCH=${1:-scarthgap}
SPDX_VERSION=${2:-3.0}
BUILD_TARGET=${3:-core-image-minimal}

(cd "$(dirname "${BASH_SOURCE[0]}")" && \
  docker build --progress plain --build-arg YOCTO_BRANCH=$YOCTO_BRANCH --build-arg SPDX_VERSION=$SPDX_VERSION --build-arg BUILD_TARGET=$BUILD_TARGET -o type=local,dest=. . && \
  tar -xf spdx-json-files.tar.zst -O --wildcards "tmp/deploy/images/qemux86-64/$BUILD_TARGET-qemux86-64.rootfs-*.spdx.json" > input/$BUILD_TARGET-qemux86-64.rootfs.spdx.json
)
