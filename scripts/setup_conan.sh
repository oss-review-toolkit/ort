#!/bin/bash
#
# Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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
#

conan_option=${CONAN_MAJOR_VERSION:-2}

# Since this script is installed with the name "conan", there is a risk of infinite recursion if pyenv is not available
# on the PATH, which can occur when setting up a development environment. To prevent this, check for recursive calls.
if [[ "$CONAN_RECURSIVE_CALL" -eq 1 ]]; then
    echo "Recursive call detected. Exiting."
    exit 1
fi

# Setup pyenv
eval "$(pyenv init - --no-rehash bash)"
eval "$(pyenv virtualenv-init -)"

# Setting up Conan 1.x
if [[ "$conan_option" -eq 1 ]]; then # Setting up Conan 1.x series
    pyenv activate conan
    # Docker has modern libc
    CONAN_RECURSIVE_CALL=1 conan profile update settings.compiler.libcxx=libstdc++11 ort-default
elif [[ "$conan_option" -eq 2 ]]; then # Setting up Conan 2.x series
    pyenv activate conan2
fi  

# Runs conan from activated profile
CONAN_RECURSIVE_CALL=1 conan "$@"

