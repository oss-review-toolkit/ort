#!/bin/sh
#
# Copyright (C) 2022 Bosch.IO GmbH
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

# Usage:
# 
# The first argument to this script is passed as-is to `docker run`. If multiple arguments shall be passed, they must
# be quoted, like `scripts/docker_run.sh "-it --entrypoint /bin/sh"`.
#
# Any following arguments are passed as-is to `ort`. If no Docker arguments shall be passed, use empty quotes like
# `scripts/docker_run.sh "" analyze --help`.

DOCKER_ARGS=$1
shift 2> /dev/null
ORT_ARGS=$@

DOCKER_RUN_AS_USER="-v /etc/group:/etc/group:ro -v /etc/passwd:/etc/passwd:ro -v $HOME:$HOME -u $(id -u):$(id -g)"

[ -n "$http_proxy" ] && HTTP_PROXY_ENV="-e http_proxy"
[ -n "$https_proxy" ] && HTTPS_PROXY_ENV="-e https_proxy"

# Mount the project root into the image to run the command task from there.
docker run $DOCKER_ARGS $DOCKER_RUN_AS_USER $HTTP_PROXY_ENV $HTTPS_PROXY_ENV -v $PWD:/workdir -w /workdir ort $ORT_ARGS
