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
import re
import sys
from time import sleep
from typing import Any
from urllib.parse import parse_qs, urlparse

import requests
from requests.structures import CaseInsensitiveDict
from rich import print

""" Use current Github API to list packages
    in registry and remove all but last 3 or custom
    set number of packages.
    Reference: https://docs.github.com/en/rest/packages/packages?apiVersion=2022-11-28#about-github-packages
"""

dry_run: bool = False if os.getenv("INPUT_DRY_RUN") == "false" else True
input_keep: str | None = os.getenv("INPUT_KEEP")
org = os.getenv("GITHUB_REPOSITORY_OWNER")
input_packages: str | None = os.getenv("INPUT_PACKAGES")
token = os.getenv("INPUT_TOKEN")
ignore_skip: bool =  True if os.getenv("INPUT_IGNORE_SKIP_TAGGED") == "true" else False

if not input_packages:
    print(":cross_mark: No packages input.")
    sys.exit(1)

packages = input_packages.split(",")
keep: int = int(input_keep) if input_keep else 0

headers = {
    "Accept": "application/vnd.github+json",
    "Authorization": f"Bearer {token}",
    "X-GitHub-Api-Version": "2022-11-28",
}

# Assembly organization packages url string
pkg_url: str = f"https://api.github.com/orgs/{org}/packages"

# List of packages that will be deleted
urls_to_be_deleted: list = []

# Exclusion image list
exclusion_list: list = []


def get_last_page(headers: CaseInsensitiveDict[str]) -> int:
    """
    Get the last page number from the headers.

    Args:
        headers (CaseInsensitiveDict[str]): The headers containing the link information.

    Returns:
        int: The last page number.

    """
    if "link" not in headers:
        return 1

    links = headers["link"].split(", ")

    last_page = None
    for link in links:
        if 'rel="last"' in link:
            last_page = link
            break

    if last_page:
        parsed_url = urlparse(
            last_page[last_page.index("<") + 1 : last_page.index(">")]
        )
        return int(parse_qs(parsed_url.query)["page"][0])

    return 1


def get_package_layers(package: str, tag: str) -> None:
    url = f"https://ghcr.io/v2/{org}/{package}/manifests/{tag}"

    # Get ghcr temprary token
    ghcr_headers = {"Authorization": f"Bearer {token}"}
    auth_response = requests.get(
        f"https://ghcr.io/token?service=ghcr.io&scope=repository:{org}/ort:pull",
        headers=ghcr_headers,
    )
    if auth_response.status_code == 200:
        access_token = auth_response.json()["token"]

        ghcr_headers = {
            "Authorization": f"Bearer {access_token}",
            "Accept": "application/vnd.oci.image.index.v1+json",
        }
        url = f"https://ghcr.io/v2/{org}/{package}/manifests/{tag}"
        if "DEBUG" in os.environ:
            print(url)
        response = requests.get(url, headers=ghcr_headers)

        main_manifest: dict[str, Any] = {}
        if response.status_code == 200:
            main_manifest: dict[str, Any] = response.json()
        else:
            print(f"Failed to get manifest: {response.status_code}, {response.text}")

        for manifest in main_manifest["manifests"]:
            if "platform" in manifest and manifest["platform"]["architecture"] in [
                "amd64",
                "arm64",
            ]:
                ghcr_headers["Accept"] = "application/vnd.oci.image.manifest.v1+json"
                url = (
                    f"https://ghcr.io/v2/{org}/{package}/manifests/{manifest['digest']}"
                )
                response = requests.get(url, headers=ghcr_headers)
                if "DEBUG" in os.environ:
                    from rich.pretty import pprint

                    pprint(response.json())
                layer_manifest = response.json()
                if 'layers' in layer_manifest:
                    for layer in layer_manifest['layers']:
                        exclusion_list.append(layer['digest'])
                        print(f":locked: Added digest to exclusion list {layer['digest']}")



def delete_packages():
    """
    Deletes packages from the package registry.

    This function iterates over the packages and deletes them from the package registry.
    It retrieves the versions of each package, sorts them by ID, and deletes the excess versions
    based on the specified 'keep' value. It also skips deleting the latest or non-snapshot tagged images.
    The function prints the status of each deletion operation and the total number of packages deleted.

    Args:
        None

    Returns:
        None
    """
    # Number of packages deleted
    packages_deleted: int = 0

    for package in packages:
        # Start page is 1 as stated by documentation
        url = f"{pkg_url}/container/{package.replace('/', '%2F')}/versions?page=1&per_page=50"

        # Get the header
        response = requests.head(url, headers=headers)
        pages: int = get_last_page(response.headers)

        for page in range(pages, 0, -1):
            print(f"Page: {page}")
            url = f"{pkg_url}/container/{package.replace('/', '%2F')}/versions?page={page}&per_page=50"

            try:
                response = requests.get(url, headers=headers)
            except requests.exceptions.RequestException as e:
                print(f":cross_mark: Connection Error. {e}")
                sys.exit(1)

            if response.status_code == 404:
                print(f":cross_mark: Not found - {url}")
                continue
            elif response.status_code == 401:
                print(f":cross_mark: Requires authentication - {url}")
                sys.exit(1)
            elif response.status_code == 403:
                print(f":cross_mark: Forbidden - {url}")
                sys.exit(1)

            # Sort all images on id.
            images = sorted(response.json(), key=lambda x: x["id"], reverse=True)

            # Slice and remove all
            if len(images) > keep:
                for image in images if page != 1 else images[keep + 1 :]:
                    url = f"{pkg_url}/container/{package.replace('/', '%2F')}/versions/{image['id']}"

                    # Never remove latest or non snapshot tagged images
                    if restrict_delete_tags(image["metadata"]["container"]["tags"]):
                        print(
                            f":package: Skip tagged {package} id {image['id']} tags {image['metadata']['container']['tags']}"
                        )
                        # Mark sublayers to not be deleted
                        get_package_layers(
                            package, image["metadata"]["container"]["tags"][0]
                        )
                        continue
                    urls_to_be_deleted.append(url)
                    tags = image["metadata"]["container"]["tags"]

                    if tags:
                        print(
                            f":white_heavy_check_mark: Deleted tagged package {package} version id {image['id']}"
                            f" with tags {tags}."
                        )
                    else:
                        print(
                            f":white_heavy_check_mark: Deleted untagged package {package} version id {image['id']}"
                        )
                # Make a slow operation to avoid rate limit
                sleep(1)

    # Effectively delete the packages
    if not dry_run:
        for url in urls_to_be_deleted:
            response = requests.delete(url, headers=headers)
            if response.status_code == 404:
                print(f":cross_mark: Failed to delete package {url}.")
                continue
            elif response.status_code == 401:
                print(f":cross_mark: Requires authentication - {url}")
                sys.exit(1)
            elif response.status_code == 403:
                print(f":cross_mark: Forbidden - {url}")
                sys.exit(1)

            packages_deleted = packages_deleted + 1
            # Make a slow operation to avoid rate limit
            sleep(1)

    print(f":package: Deleted {packages_deleted} packages in the organization.")


def restrict_delete_tags(tags: list) -> bool:
    if not tags:
        return False
    for tag in tags:
        if tag == "latest":
            return True
        elif ".sha." in tag:
            return False
        elif "SNAPSHOT" in tag:
            return False
        else:
            pattern = re.compile(r"^\d+\.\d+\.\d+$")
            if pattern.match(tag):
                return True
    return False


if __name__ == "__main__":
    delete_packages()
