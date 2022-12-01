#!/bin/sh
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

# This script imports all certificate files matching the path / file prefix provided as the argument to both the JVM
# keystore and the operating system's certificate store.

FILE_PREFIX=$1

if [ -z "$FILE_PREFIX" ]; then
    echo "No certificates specified. Skipping the import."
    exit 0
fi

echo "Importing certificates from $FILE_PREFIX..."

KEYTOOL=$(realpath "$(command -v keytool)")
KEYTOOL_DIR=$(dirname "$KEYTOOL")

for KEYSTORE_CANDIDATE in "$KEYTOOL_DIR/../lib/security/cacerts" "$KEYTOOL_DIR/../jre/lib/security/cacerts"; do
    KEYSTORE_CANDIDATE_PATH=$(realpath -m "$KEYSTORE_CANDIDATE")
    if [ -f "$KEYSTORE_CANDIDATE_PATH" ]; then
        KEYSTORE=$KEYSTORE_CANDIDATE_PATH
        break
    fi
done

if [ -n "$KEYSTORE" ]; then
    for CRT_FILE in "$FILE_PREFIX"*; do
        echo "Adding the following certificate from '$CRT_FILE' to the JVM's certificate store at '$KEYSTORE':"
        cat "$CRT_FILE"

        ALIAS=$(basename "$CRT_FILE" .crt)
        $KEYTOOL -importcert -noprompt -trustcacerts -alias "$ALIAS" -file "$CRT_FILE" \
            -keystore "$KEYSTORE" -storepass changeit
    done
else
    echo "No JVM keystore found, skipping the import."
fi

# Also add the certificates to the system certificates, e.g. for curl to work.
echo "Adding certificates to the system certificates..."
cp -r "$FILE_PREFIX"* /usr/local/share/ca-certificates/
update-ca-certificates
