#!/bin/sh
#
# Copyright (C) 2020 Bosch.IO GmbH
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

writeProxyStringToGradleProps () {
    PROXY=$1
    PROTOCOL=$2
    FILE=$3

    HOST=${PROXY%:*}
    HOST=${HOST#*//}

    PORT=${PROXY##*:}
    [ "$PORT" -ge 0 ] 2>/dev/null || PORT=80

    grep -qF "systemProp.$PROTOCOL.proxy" $FILE 2>/dev/null && return 1

    mkdir -p $(dirname $FILE)

    # TODO: Support proxy authentication once Gradle does, see https://github.com/gradle/gradle/issues/5052.
    cat <<- EOF >> $FILE
	systemProp.$PROTOCOL.proxyHost=$HOST
	systemProp.$PROTOCOL.proxyPort=$PORT
	EOF
}

writeProxyEnvToGradleProps () {
    GRADLE_PROPS="$HOME/.gradle/gradle.properties"

    if [ -n "$http_proxy" ]; then
        echo "Setting HTTP proxy $http_proxy for Gradle in file '$GRADLE_PROPS'..."
        if ! writeProxyStringToGradleProps $http_proxy "http" $GRADLE_PROPS; then
            echo "Not replacing existing HTTP proxy."
        fi
    fi

    if [ -n "$https_proxy" ]; then
        echo "Setting HTTPS proxy $https_proxy for Gradle in file '$GRADLE_PROPS'..."
        if ! writeProxyStringToGradleProps $https_proxy "https" $GRADLE_PROPS; then
            echo "Not replacing existing HTTPS proxy."
        fi
    fi
}

writeProxyEnvToGradleProps
