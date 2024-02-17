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

import hashlib
import os
import sys

import requests
from requests import Response

""" Use current GitHub API to check if a container image with the
    given name and version exists.
"""

token = os.getenv("INPUT_TOKEN")
owner = os.getenv("GITHUB_REPOSITORY_OWNER")
name = os.getenv("INPUT_NAME")
base_version = os.getenv("INPUT_VERSION")
build_args = os.getenv("BUILD_ARGS")
invalidate_cache = True if os.getenv("INVALIDATE_CACHE") else False
unique_id = hashlib.sha256(build_args.encode()).hexdigest() if build_args else "uniq"

# We base the version on the base_version and the unique_id
version = f"{base_version}-sha.{unique_id[:8]}"

# In case of need invalidate the cache from images we just return the version
if invalidate_cache:
    print(version)
    sys.exit(0)

headers: dict[str, str] = {
    "Accept": "application/vnd.github+json",
    "Authorization": f"Bearer {token}",
    "X-GitHub-Api-Version": "2022-11-28",
}

url: str = f"https://api.github.com/users/{owner}"
response = requests.get(url, headers=headers)
data: Response = response.json()

if data.get("type") == "Organization":
    url = (
        f"https://api.github.com/orgs/{owner}/packages/container/ort%2F{name}/versions"
    )
else:
    url = f"https://api.github.com/user/packages/container/ort%2F{name}/versions"

response = requests.get(url, headers=headers)
if response.status_code == 404:
    print("none")
else:
    data = response.json()
    versions = [
        item
        for sublist in [v["metadata"]["container"]["tags"] for v in data]
        if sublist
        for item in sublist
    ]
    if version in versions:
        print("found")
    else:
        print(version)
