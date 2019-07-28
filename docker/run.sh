#!/bin/sh
#
# Copyright (C) 2019 Bosch Software Innovations GmbH
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# SPDX-License-Identifier: Apache-2.0
# License-Filename: LICENSE

# Get the absolute directory this script resides in.
SCRIPT_DIR="$(cd "$(dirname $0)" && pwd)"

# Get the absolute directory of the project root.
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

. $SCRIPT_DIR/lib

# If required, first build the distribution.
ORT_VERSION=$(cat $PROJECT_DIR/model/src/main/resources/VERSION)
if [ ! -f "$PROJECT_DIR/cli/build/distributions/ort-$ORT_VERSION.tar" ]; then
    docker/build.sh || die "Failed to build ORT."
    ORT_VERSION=$(cat $PROJECT_DIR/model/src/main/resources/VERSION)
fi

echo "Running ORT version $ORT_VERSION..."

DOCKER_ARGS=$1
if [ -n "$DOCKER_ARGS" ]; then
    shift
    ORT_ARGS=$@
fi

(cd $PROJECT_DIR && \
    docker build --build-arg ORT_VERSION=$ORT_VERSION -t ort:latest -f docker/run/Dockerfile cli/build/distributions && \
    runAsUser "$DOCKER_ARGS" ort "$ORT_ARGS"
)
