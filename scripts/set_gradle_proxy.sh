#!/bin/bash
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
    local PROXY=$1
    local PROTOCOL=$2
    local FILE=$3

    # Strip the port.
    local HOST=${PROXY%:*}
    # Strip the protocol.
    HOST=${HOST#*//}
    # Extract authentication info.
    local AUTH=${HOST%%@*}
    if [ "$AUTH" != "$HOST" ]; then
        # Strip authentication info.
        HOST=${HOST#$AUTH@}
        # Extract the user.
        local USER=${AUTH%%:*}
        # Extract the password.
        local PASSWORD=${AUTH#*:}
    fi

    local PORT=${PROXY##*:}
    [ "$PORT" -ge 0 ] 2>/dev/null || PORT=80

    grep -qF "systemProp.$PROTOCOL.proxy" $FILE 2>/dev/null && return 1

    mkdir -p $(dirname $FILE)

    cat <<- EOF >> $FILE
	systemProp.$PROTOCOL.proxyHost=$HOST
	systemProp.$PROTOCOL.proxyPort=$PORT
	systemProp.$PROTOCOL.proxyUser=$USER
	systemProp.$PROTOCOL.proxyPassword=$PASSWORD
	EOF
}

writeNoProxyEnvToGradleProps () {
    local HOSTS=$1
    local FILE=$2

    grep -qF "systemProp.http.nonProxyHosts" $FILE 2>/dev/null && return 1

    # Gradle / JVM expects a list separated by pipes instead of the comma that
    # is used in shell environments
    echo "systemProp.http.nonProxyHosts=${HOSTS//,/\|}" >> $FILE
}

writeProxyEnvToGradleProps () {
    local GRADLE_PROPS="${GRADLE_USER_HOME:-$HOME/.gradle}/gradle.properties"

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

    if [ -n "$no_proxy" ]; then
        echo "Setting proxy exemptions $no_proxy for Gradle in file '$GRADLE_PROPS'..."
        if ! writeNoProxyEnvToGradleProps $no_proxy $GRADLE_PROPS; then
            echo "Not replacing existing proxy exemptions."
        fi
    fi
}

writeProxyEnvToGradleProps
