#!/bin/bash

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

# Global functions
add_local_path () {
    case ":${PATH:=$1}:" in
        *:"$1":*) ;;
        *) PATH="$1:$PATH" ;;
    esac;
}

export -f add_local_path

