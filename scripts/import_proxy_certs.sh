#/!bin/sh
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

if [ -z "$https_proxy" ]; then
    echo "Skipping the import of certificates as no HTTPS proxy is set."
    exit
fi

CONNECT_SERVER="jcenter.bintray.com:443"

FILE="proxy.crt"
FILE_PREFIX="proxy-"

REGEX_BEGIN="/^-----BEGIN CERTIFICATE-----$/"
REGEX_END="/^-----END CERTIFICATE-----$"

# Pick a server to connect to that is used during the Gradle build, and which reports the proxy's certificate instead of
# its own.
echo "Getting the proxy's certificates..."
openssl s_client -showcerts -proxy ${https_proxy#*//} -connect $CONNECT_SERVER | \
    sed -n "$REGEX_BEGIN,$REGEX_END/p" > $FILE

# Split the potentially multiple certificates into multiple files to avoid only the first certificate being imported.
echo "Splitting proxy certificates to separate files..."
csplit -f $FILE_PREFIX -b "%02d.crt" -z $FILE "$REGEX_BEGIN" "{*}"

KEYTOOL=$(realpath $(command -v keytool))

for KEYSTORE_CANDIDATE in "$(realpath -m $(dirname $KEYTOOL)/../lib/security/cacerts)" "$(realpath -m $(dirname $KEYTOOL)/../jre/lib/security/cacerts)"; do
    if [ -f "$KEYSTORE_CANDIDATE" ]; then
        KEYSTORE=$KEYSTORE_CANDIDATE
        break
    fi
done

if [ -z "$KEYSTORE" ]; then
    echo "No cacert keystore found, quitting."
    exit
fi

for CRT_FILE in $FILE_PREFIX*; do
    echo "Adding the following proxy certificate from '$CRT_FILE' to the JRE's certificate store at '$KEYSTORE':"
    cat $CRT_FILE

    $KEYTOOL -importcert -noprompt -trustcacerts -alias $CRT_FILE -file $CRT_FILE -keystore $KEYSTORE -storepass changeit
done

# Also add the proxy certificates to the system certificates, e.g. for curl to work.
echo "Adding proxy certificates to the system certificates..."
cp $FILE_PREFIX* /usr/local/share/ca-certificates/
update-ca-certificates
