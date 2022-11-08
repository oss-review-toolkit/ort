#!/bin/bash
#
# Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

set -e -o pipefail

DOCKER_ARGS=$*

GIT_ROOT=$(git rev-parse --show-toplevel)
GIT_REVISION=$(git describe --abbrev=10 --always --tags --dirty --match=[0-9]*)
PLATFORM=${PLATFORM:-linux/amd64}
ORG_BASE_NAME="${ORG_BASE_NAME:-oss-review-toolkit}"

# Parse and transform versions from unique .versions file
# shellcheck disable=SC1091
source .versions
VERSION_ARGS=$(xargs printf -- '--build-arg %s\n' < .versions)

# Auxiliary function to build intermediary and final image
# Usage:
# docker_build <message> <target> <tag> <image_version>
docker_build() {
    echo "$1"
    # shellcheck disable=SC2086
    docker buildx build \
        -f "$GIT_ROOT/Dockerfile" \
        --target $2 \
        --platform "$PLATFORM" \
        --tag "$ORG_BASE_NAME/$3:latest" \
        --tag "$ORG_BASE_NAME/$3:$4" \
        --tag "ghcr.io/$ORG_BASE_NAME/$3:latest" \
        --build-arg ORT_VERSION="$GIT_REVISION" \
        $VERSION_ARGS \
        $DOCKER_ARGS \
        "$GIT_ROOT"
}

# Set NOBASE on terminal to skipt straight to ort container build
# if all intermediary and base images are there
if [ -z "${NOBASE+x}" ]; then
    docker_build "Building base image" base base "$GIT_REVISION"
    for lang in python ruby nodejs rust go haskell_stack android_cmd dart sbt; do
        if [ "${BASH_VERSION:0:1}" == "3" ]; then
            # Mac has old bash
            lang_version="$(tr '[:lower:]' '[:upper:]' <<< ${lang})_VERSION"
        else
            lang_version="${lang^^}_VERSION"
        fi
        docker_build "Building support language: $lang" $lang $lang "${!lang_version}"
    done
fi
docker_build "Building ort binary" ort ortdist "$GIT_REVISION"
docker_build "Building ort components" components components "$GIT_REVISION"
docker_build "Building ort combined runtime image" run ort "$GIT_REVISION"

