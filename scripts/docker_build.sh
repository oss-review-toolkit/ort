#!/usr/bin/env bash
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
ORT_VERSION=$("$GIT_ROOT/gradlew" -q properties --property version | sed -nr "s/version: (.+)/\1/p")
DOCKER_IMAGE_ROOT="${DOCKER_IMAGE_ROOT:-ghcr.io/oss-review-toolkit}"

echo "Setting ORT_VERSION to $ORT_VERSION."

# shellcheck disable=SC2046
export $(cat "$GIT_ROOT/.env.versions" | xargs)

# ---------------------------
# image_build function
# Usage ( position parameters):
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
        --target "$target" \
        --tag "${DOCKER_IMAGE_ROOT}/$name:$version" \
        --tag "${DOCKER_IMAGE_ROOT}/$name:latest" \
        "$@" .
}

# Minimimal ORT image
# This is the base image for ORT and contains the minimal
# set of tools required to run ORT including main binaries.

# Base
image_build base ort/base "${JAVA_VERSION}-jdk-${UBUNTU_VERSION}" \
    --build-arg UBUNTU_VERSION="$UBUNTU_VERSION" \
    --build-arg JAVA_VERSION="$JAVA_VERSION" \
    "$@"

# Python
image_build python ort/python "$PYTHON_VERSION" \
    --build-arg PYTHON_VERSION="$PYTHON_VERSION" \
    --build-arg CONAN_VERSION="$CONAN_VERSION" \
    --build-arg CONAN2_VERSION="$CONAN2_VERSION" \
    --build-arg PYTHON_INSPECTOR_VERSION="$PYTHON_INSPECTOR_VERSION" \
    --build-arg PYTHON_PIPENV_VERSION="$PYTHON_PIPENV_VERSION" \
    --build-arg PYTHON_POETRY_VERSION="$PYTHON_POETRY_VERSION" \
    --build-arg PIP_VERSION="$PIP_VERSION" \
    --build-arg SCANCODE_VERSION="$SCANCODE_VERSION" \
    --build-context "base=docker-image://${DOCKER_IMAGE_ROOT}/ort/base:latest" \
    "$@"

# Nodejs
image_build nodejs ort/nodejs "$NODEJS_VERSION" \
    --build-arg NODEJS_VERSION="$NODEJS_VERSION" \
    --build-arg BOWER_VERSION="$BOWER_VERSION" \
    --build-arg NPM_VERSION="$NPM_VERSION" \
    --build-arg PNPM_VERSION="$PNPM_VERSION" \
    --build-arg YARN_VERSION="$YARN_VERSION" \
    --build-context "base=docker-image://${DOCKER_IMAGE_ROOT}/ort/base:latest" \
    "$@"

# Rust
image_build rust ort/rust "$RUST_VERSION" \
    --build-arg RUST_VERSION="$RUST_VERSION" \
    --build-context "base=docker-image://${DOCKER_IMAGE_ROOT}/ort/base:latest" \
    "$@"

# Ruby
image_build ruby ort/ruby "$RUBY_VERSION" \
    --build-arg RUBY_VERSION="$RUBY_VERSION" \
    --build-arg COCOAPODS_VERSION="$COCOAPODS_VERSION" \
    --build-context "base=docker-image://${DOCKER_IMAGE_ROOT}/ort/base:latest" \
    "$@"

# Golang
image_build golang ort/golang "$GO_VERSION" \
    --build-arg GO_VERSION="$GO_VERSION" \
    --build-context "base=docker-image://${DOCKER_IMAGE_ROOT}/ort/base:latest" \
    "$@"

# Runtime ORT image
image_build minimal ort-minimal "$ORT_VERSION" \
    --build-arg ORT_VERSION="$ORT_VERSION" \
    --build-arg NODEJS_VERSION="$NODEJS_VERSION" \
    --build-context "base=docker-image://${DOCKER_IMAGE_ROOT}/ort/base:latest" \
    --build-context "python=docker-image://${DOCKER_IMAGE_ROOT}/ort/python:latest" \
    --build-context "nodejs=docker-image://${DOCKER_IMAGE_ROOT}/ort/nodejs:latest" \
    --build-context "rust=docker-image://${DOCKER_IMAGE_ROOT}/ort/rust:latest" \
    --build-context "golang=docker-image://${DOCKER_IMAGE_ROOT}/ort/golang:latest" \
    --build-context "ruby=docker-image://${DOCKER_IMAGE_ROOT}/ort/ruby:latest" \
    "$@"

# Android
# shellcheck disable=SC1091
image_build android ort/android "$ANDROID_CMD_VERSION" \
    --build-arg ANDROID_CMD_VERSION="$ANDROID_CMD_VERSION" \
    --build-context "base=docker-image://${DOCKER_IMAGE_ROOT}/ort/base:latest" \
    "$@"

# Swift
image_build swift ort/swift "$SWIFT_VERSION" \
    --build-arg SWIFT_VERSION="$SWIFT_VERSION" \
    --build-context "base=docker-image://${DOCKER_IMAGE_ROOT}/ort/base:latest" \
    "$@"

# SBT
image_build scala ort/scala "$SBT_VERSION" \
    --build-arg SBT_VERSION="$SBT_VERSION" \
    --build-context "base=docker-image://${DOCKER_IMAGE_ROOT}/ort/base:latest" \
    "$@"

# Dart
image_build dart ort/dart "$DART_VERSION" \
    --build-arg DART_VERSION="$DART_VERSION" \
    --build-context "base=docker-image://${DOCKER_IMAGE_ROOT}/ort/base:latest" \
    "$@"

# Dotnet
image_build dotnet ort/dotnet "$DOTNET_VERSION" \
    --build-arg DOTNET_VERSION="$DOTNET_VERSION" \
    --build-arg NUGET_INSPECTOR_VERSION="$NUGET_INSPECTOR_VERSION" \
    --build-context "base=docker-image://${DOCKER_IMAGE_ROOT}/ort/base:latest" \
    "$@"

# Haskell
image_build haskell ort/haskell "$HASKELL_STACK_VERSION" \
    --build-arg HASKELL_STACK_VERSION="$HASKELL_STACK_VERSION" \
    --build-context "base=docker-image://${DOCKER_IMAGE_ROOT}/ort/base:latest" \
    "$@"

# Main runtime ORT image
image_build run ort "$ORT_VERSION" \
    --build-arg ORT_VERSION="$ORT_VERSION" \
    --build-context "minimal=docker-image://${DOCKER_IMAGE_ROOT}/ort-minimal:${ORT_VERSION}" \
    --build-context "sbt=docker-image://${DOCKER_IMAGE_ROOT}/ort/sbt:latest" \
    --build-context "dotnet=docker-image://${DOCKER_IMAGE_ROOT}/ort/dotnet:latest" \
    --build-context "swift=docker-image://${DOCKER_IMAGE_ROOT}/ort/swift:latest" \
    --build-context "android=docker-image://${DOCKER_IMAGE_ROOT}/ort/android:latest" \
    --build-context "dart=docker-image://${DOCKER_IMAGE_ROOT}/ort/dart:latest" \
    --build-context "haskell=docker-image://${DOCKER_IMAGE_ROOT}/ort/haskell:latest" \
    --build-context "scala=docker-image://${DOCKER_IMAGE_ROOT}/ort/scala:latest" \
    "$@"
