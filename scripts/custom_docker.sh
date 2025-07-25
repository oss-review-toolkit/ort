#!/usr/bin/env bash

# Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

# Current script dir
SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)

# Docker root
DOCKER_IMAGE_ROOT="${DOCKER_IMAGE_ROOT:-ghcr.io/oss-review-toolkit}"

# Define the list of valid components
valid_components=("android" "swift" "sbt" "dart" "dotnet" "php" "haskell")

# Define the Dockerfile template
dockerfile_template="FROM ${DOCKER_IMAGE_ROOT}/ort-minimal\n"

# Default output file
output_file="Dockerfile.custom"

function usage() {
    echo "Usage: $0 -c <component1> [<component2> ...] -o <output_file>"
    echo "Options:"
    echo "  -c <component1> [<component2> ...]: List of language components to include in the Dockerfile: ${valid_components[*]}"
    echo "  -output <output_file>: Output file for the generated Dockerfile, Defaults to '$output_file'."
    echo "  -h: Display this help message"
}

# Parse the command-line options
while [[ $# -gt 0 ]]; do
    case "$1" in
    -c)
        shift
        components=("$@")
        break
        ;;
    -o)
        shift
        output_file=$1
        ;;
    -h)
        usage
        exit 0
        ;;
    *)
        echo "Invalid option: $1"
        usage
        exit 1
        ;;
    esac
    shift
done

# Check if the required options are present
if [[ -z "${components[*]}" ]]; then
    echo "Missing required options"
    usage
    exit 1
fi

# Check if the components are valid
for component in "${components[@]}"; do
    valid=false
    for valid_component in "${valid_components[@]}"; do
        if [[ "${valid_component}" == "${component}" ]]; then
            valid=true
            break
        fi
    done

    if [[ "${valid}" == false ]]; then
        echo "Invalid component: ${component}"
        usage
        exit 1
    fi
done

# Write the Dockerfile to the output file
echo -e "${dockerfile_template}" >"${output_file}"

# Add the components to the Dockerfile template
for component in "${components[@]}"; do
    # Add the component to the custom Dockerfile
    cat "${SCRIPT_DIR}/docker_snippets/${component}.snippet" >>"${output_file}"
    echo -e "\n" >>"${output_file}"
done
