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

SELF=${BASH_SOURCE[0]}

if [ $# -ne 1 ]; then
    echo "Usage: $(basename "$SELF") <GRADLE_PROJECT_DIR>"
    exit 1
fi

GRADLE_PROJECT_DIR=$1
PLUGIN_PROJECT_DIR=$(dirname "$SELF")
GIT_ROOT=$(git rev-parse --show-toplevel)

"$GIT_ROOT"/gradlew -q -p "$PLUGIN_PROJECT_DIR" -Pversion=0 fatJar
PLUGIN_JAR="$GIT_ROOT"/plugins/package-managers/gradle-plugin/build/libs/gradle-plugin-0-fat.jar

TEMPLATE_INIT_SCRIPT="$GIT_ROOT"/plugins/package-managers/gradle-inspector/src/main/resources/template.init.gradle
PATCHED_INIT_SCRIPT=$(mktemp --suffix .init.gradle)

sed "s,<REPLACE_PLUGIN_JAR>,$PLUGIN_JAR," "$TEMPLATE_INIT_SCRIPT" > "$PATCHED_INIT_SCRIPT"

echo "Using patched init script at '$PATCHED_INIT_SCRIPT'."

"$GIT_ROOT"/gradlew -q -I "$PATCHED_INIT_SCRIPT" -P dumpOrtModel -p "$GRADLE_PROJECT_DIR"
