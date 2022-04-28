#!/bin/sh
#
# Copyright (C) 2020 Bosch.IO GmbH
# Copyright (C) 2021 BMW CarIT GmbH
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

# Docker from ORT uses BuildKit feature

GIT_ROOT=$(git rev-parse --show-toplevel)
GIT_VERSION=$(git describe --abbrev=10 --always --tags --dirty)
ORT_DOCKER=${ORT_DOCKER:-ort}

echo "Setting ORT_VERSION to $GIT_VERSION."
DOCKER_BUILDKIT=1 docker build -f "$GIT_ROOT"/Dockerfile \
    -t "$ORT_DOCKER" \
    --build-arg ORT_VERSION="$GIT_VERSION" \
    --build-arg ANDROID_CMD_VERSION=7583922 \
    --build-arg BOWER_VERSION="1.8.12" \
    --build-arg COCOAPODS_VERSION="1.11.2" \
    --build-arg COMPOSER_VERSION="1.10.1-1" \
    --build-arg CONAN_VERSION="1.44.0" \
    --build-arg GO_DEP_VERSION="0.5.4" \
    --build-arg GO_VERSION="1.17.3" \
    --build-arg HASKELL_STACK_VERSION="2.1.3" \
    --build-arg NODEJS_VERSION="16.13.2" \
    --build-arg NPM_VERSION="7.20.6" \
    --build-arg PIPTOOL_VERSION="21.3.1" \
    --build-arg PYTHON_VERSION="3.10.4" \
    --build-arg PYTHON2_VERSION="" \
    --build-arg PYTHON_VIRTUALENV_VERSION="20.13.0" \
    --build-arg SCANCODE_VERSION="30.1.0" \
    --build-arg RUBY_VERSION="2.7.4" \
    --build-arg SBT_VERSION="1.6.1" \
    --build-arg YARN_VERSION="1.22.10" \
    "$@" \
    "$GIT_ROOT"
