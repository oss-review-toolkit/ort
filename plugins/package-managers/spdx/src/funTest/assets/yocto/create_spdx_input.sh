#!/usr/bin/env bash

# Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# SPDX-License-Identifier: Apache-2.0
# License-Filename: LICENSE

YOCTO_BRANCH=${1:-scarthgap}
SPDX_VERSION=${2:-3.0}
BUILD_TARGET=${3:-core-image-minimal}

(cd "$(dirname "${BASH_SOURCE[0]}")" && \
  docker build --progress plain --build-arg YOCTO_BRANCH=$YOCTO_BRANCH --build-arg SPDX_VERSION=$SPDX_VERSION --build-arg BUILD_TARGET=$BUILD_TARGET -o type=local,dest=. . && \
  tar -xf spdx-json-files.tar.zst -O --wildcards "tmp/deploy/images/qemux86-64/$BUILD_TARGET-qemux86-64.rootfs-*.spdx.json" > input/$BUILD_TARGET-qemux86-64.rootfs.spdx.json
)
