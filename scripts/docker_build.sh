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

GIT_ROOT=$(git rev-parse --show-toplevel)
GIT_REVISION=$($GIT_ROOT/gradlew -q properties --property version | sed -nr "s/version: (.+)/\1/p")
DOCKER_IMAGE_ROOT="${DOCKER_IMAGE_ROOT:-ghcr.io/oss-review-toolkit}"

echo "Setting ORT_VERSION to $GIT_REVISION."

# ---------------------------
# image_build function
# Usage ( position paramenters):
# image_build <target_name> <tag_name> <version> <extra_args...>

image_build() {
    local target
    local name
    local version
    target="$1"
    shift
    name="$1"
    shift
    version="$1"
    shift

    docker buildx build \
        -f "$GIT_ROOT/Dockerfile" \
        --target "$target" \
        --tag "${DOCKER_IMAGE_ROOT}/$name:$version" \
        --tag "${DOCKER_IMAGE_ROOT}/$name:latest" \
        --build-context "base=docker-image://${DOCKER_IMAGE_ROOT}/base:latest" \
        "$@" .
}

# Base
# shellcheck disable=SC1091
. .ortversions/base.versions
image_build ort-base-image base "${JAVA_VERSION}-jdk-${UBUNTU_VERSION}" \
    --build-arg UBUNTU_VERSION="$UBUNTU_VERSION" \
    --build-arg JAVA_VERSION="$JAVA_VERSION" \
    "$@"

# Python
# shellcheck disable=SC1091
. .ortversions/python.versions
image_build python python "$PYTHON_VERSION" \
    --build-arg PYTHON_VERSION="$PYTHON_VERSION" \
    --build-arg CONAN_VERSION="$CONAN_VERSION" \
    --build-arg PYTHON_INSPECTOR_VERSION="$PYTHON_INSPECTOR_VERSION" \
    --build-arg PYTHON_PIPENV_VERSION="$PYTHON_PIPENV_VERSION" \
    --build-arg PYTHON_POETRY_VERSION="$PYTHON_POETRY_VERSION" \
    --build-arg PIPTOOL_VERSION="$PIPTOOL_VERSION" \
    --build-arg SCANCODE_VERSION="$SCANCODE_VERSION" \
    "$@"

# Nodejs
# shellcheck disable=SC1091
. .ortversions/nodejs.versions
image_build nodejs nodejs "$NODEJS_VERSION" \
    --build-arg NODEJS_VERSION="$NODEJS_VERSION" \
    --build-arg BOWER_VERSION="$BOWER_VERSION" \
    --build-arg NPM_VERSION="$NPM_VERSION" \
    --build-arg PNPM_VERSION="$PNPM_VERSION" \
    --build-arg YARN_VERSION="$YARN_VERSION" \
    "$@"

# Rust
# shellcheck disable=SC1091
. .ortversions/rust.versions
image_build rust rust "$RUST_VERSION" \
    --build-arg RUST_VERSION="$RUST_VERSION" \
    "$@"

# Ruby
# shellcheck disable=SC1091
. .ortversions/ruby.versions
image_build ruby ruby "$RUBY_VERSION" \
    --build-arg RUBY_VERSION="$RUBY_VERSION" \
    --build-arg COCOAPODS_VERSION="$COCOAPODS_VERSION" \
    "$@"

# Golang
# shellcheck disable=SC1091
. .ortversions/golang.versions
image_build golang golang "$GO_VERSION" \
    --build-arg GO_VERSION="$GO_VERSION" \
    --build-arg GO_DEP_VERSION="$GO_DEP_VERSION" \
    "$@"

# Ort
image_build ortbin ortbin "$GIT_REVISION" \
    --build-arg ORT_VERSION="$GIT_REVISION" \
    "$@"

# Runtime ORT image
image_build run ort "$GIT_REVISION" \
    --build-context "python=docker-image://${DOCKER_IMAGE_ROOT}/python:latest" \
    --build-arg NODEJS_VERSION="$NODEJS_VERSION" \
    --build-context "nodejs=docker-image://${DOCKER_IMAGE_ROOT}/nodejs:latest" \
    --build-context "rust=docker-image://${DOCKER_IMAGE_ROOT}/rust:latest" \
    --build-context "golang=docker-image://${DOCKER_IMAGE_ROOT}/golang:latest" \
    --build-context "ruby=docker-image://${DOCKER_IMAGE_ROOT}/ruby:latest" \
    --build-context "ortbin=docker-image://${DOCKER_IMAGE_ROOT}/ortbin:latest" \
    "$@"

# Build adjacent language containers if ALL_LANGUAGES is set.
[ -z "$ALL_LANGUAGES" ] && exit 0

# Android
# shellcheck disable=SC1091
. .ortversions/android.versions
image_build android android "$ANDROID_CMD_VERSION" \
    --build-arg ANDROID_CMD_VERSION="$ANDROID_CMD_VERSION" \
    "$@"

# Swift
# shellcheck disable=SC1091
. .ortversions/swift.versions
image_build swift swift "$SWIFT_VERSION" \
    --build-arg SWIFT_VERSION="$SWIFT_VERSION" \
    "$@"

# SBT
# shellcheck disable=SC1091
. .ortversions/sbt.versions
image_build sbt sbt "$SBT_VERSION" \
    --build-arg SBT_VERSION="$SBT_VERSION" \
    "$@"

# Dart
# shellcheck disable=SC1091
. .ortversions/dart.versions
image_build dart dart "$DART_VERSION" \
    --build-arg DART_VERSION="$DART_VERSION" \
    "$@"

# Dotnet
# shellcheck disable=SC1091
. .ortversions/dotnet.versions
image_build dotnet dotnet "$DOTNET_VERSION" \
    --build-arg DOTNET_VERSION="$DOTNET_VERSION" \
    --build-arg NUGET_INSPECTOR_VERSION="$NUGET_INSPECTOR_VERSION" \
    "$@"

# Haskell
# shellcheck disable=SC1091
. .ortversions/haskell.versions
image_build haskell haskell "$HASKELL_STACK_VERSION" \
    --build-arg HASKELL_STACK_VERSION="$HASKELL_STACK_VERSION" \
    "$@"
