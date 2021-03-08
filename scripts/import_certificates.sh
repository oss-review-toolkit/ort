#!/bin/sh
#
# Copyright (C) 2020-2021 Bosch.IO GmbH
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

FILE_PREFIX=$1

if [ -z "$FILE_PREFIX" ]; then
  echo "No certificates specified. Skipping installation."
  exit 0
fi

echo "Import certificates $FILE_PREFIX:"

KEYTOOL=$(realpath $(command -v keytool))

for KEYSTORE_CANDIDATE in "$(realpath -m $(dirname $KEYTOOL)/../lib/security/cacerts)" "$(realpath -m $(dirname $KEYTOOL)/../jre/lib/security/cacerts)"; do
    if [ -f "$KEYSTORE_CANDIDATE" ]; then
        KEYSTORE=$KEYSTORE_CANDIDATE
        break
    fi
done

if [ -n "$KEYSTORE" ]; then
    for CRT_FILE in $FILE_PREFIX*; do
        echo "Adding the following certificate from '$CRT_FILE' to the JRE's certificate store at '$KEYSTORE':"
        cat $CRT_FILE

        ALIAS=$(basename $CRT_FILE .crt)
        $KEYTOOL -importcert -noprompt -trustcacerts -alias $ALIAS -file $CRT_FILE -keystore $KEYSTORE -storepass changeit
    done
else
    echo "No JVM keystore found, skipping the import."
fi

# Also add the certificates to the system certificates, e.g. for curl to work.
echo "Adding certificates to the system certificates..."
cp -r "$FILE_PREFIX" /usr/local/share/ca-certificates/
update-ca-certificates
