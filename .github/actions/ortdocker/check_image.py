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

import os

import requests

""" Use current GitHub API to check if a container image with the
    given name and version exists.
"""

token = os.getenv("INPUT_TOKEN")
org = os.getenv("GITHUB_REPOSITORY_OWNER")
name = os.getenv("INPUT_NAME")
version = os.getenv("INPUT_VERSION")

url = f"https://api.github.com/orgs/{org}/packages/container/ort%2F{name}/versions"

headers = {
    "Accept": "application/vnd.github+json",
    "Authorization": f"Bearer {token}",
}
response = requests.get(url, headers=headers)
if response.status_code == 404:
    print("none")
else:
    versions = [
        item
        for sublist in [v["metadata"]["container"]["tags"] for v in response.json()]
        if sublist
        for item in sublist
    ]
    if version in versions:
        print("found")
    else:
        print("none")
