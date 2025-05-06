/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package org.ossreviewtoolkit.plugins.packagemanagers.node

import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.plugins.packagemanagers.node.PackageJson.Repository

class UtilsTest : WordSpec({
    "expandNpmShortcutUrl()" should {
        "do nothing for empty URLs" {
            expandShortcutUrl("") shouldBe ""
        }

        "return valid URLs unmodified" {
            expandShortcutUrl("https://github.com/oss-review-toolkit/ort") shouldBe
                "https://github.com/oss-review-toolkit/ort"
        }

        "properly handle NPM shortcut URLs" {
            val packages = mapOf(
                "npm/npm"
                    to "https://github.com/npm/npm.git",
                "mochajs/mocha#4727d357ea"
                    to "https://github.com/mochajs/mocha.git#4727d357ea",
                "user/repo#feature/branch"
                    to "https://github.com/user/repo.git#feature/branch",
                "github:snyk/node-tap#540c9e00f52809cb7fbfd80463578bf9d08aad50"
                    to "https://github.com/snyk/node-tap.git#540c9e00f52809cb7fbfd80463578bf9d08aad50",
                "gist:11081aaa281"
                    to "https://gist.github.com/11081aaa281",
                "bitbucket:example/repo"
                    to "https://bitbucket.org/example/repo.git",
                "gitlab:another/repo"
                    to "https://gitlab.com/another/repo.git"
            )

            packages.entries.forAll { (actualUrl, expectedUrl) ->
                expandShortcutUrl(actualUrl) shouldBe expectedUrl
            }
        }

        "not mess with crazy URLs" {
            val packages = mapOf(
                "git@github.com/cisco/node-jose.git"
                    to "git@github.com/cisco/node-jose.git",
                "https://git@github.com:hacksparrow/node-easyimage.git"
                    to "https://git@github.com:hacksparrow/node-easyimage.git",
                "github.com/improbable-eng/grpc-web"
                    to "github.com/improbable-eng/grpc-web"
            )

            packages.entries.forAll { (actualUrl, expectedUrl) ->
                expandShortcutUrl(actualUrl) shouldBe expectedUrl
            }
        }
    }

    "fixNpmDownloadUrl()" should {
        "replace HTTP with HTTPS for the NPM registry only" {
            "http://registry.npmjs.org/babel-cli/-/babel-cli-6.26.0.tgz".fixDownloadUrl() shouldBe
                "https://registry.npmjs.org/babel-cli/-/babel-cli-6.26.0.tgz"
            "http://oss-review-toolkit.org/".fixDownloadUrl() shouldBe "http://oss-review-toolkit.org/"
        }

        "correct Artifactory API URLS" {
            "http://my.repo/artifactory/api/npm/npm/all".fixDownloadUrl() shouldBe
                "http://my.repo/artifactory/npm/all"
        }
    }

    "parseNpmVcsInfo()" should {
        "get VCS information from a repository instance with type, url and directory" {
            val packageJson = PackageJson(
                gitHead = "bar",
                repository = Repository(
                    type = "Git",
                    url = "https://example.com/",
                    directory = "foo"
                )
            )

            parseVcsInfo(packageJson) shouldBe VcsInfo(
                VcsType.GIT,
                "https://example.com/",
                "bar",
                "foo"
            )
        }

        "get VCS information from a repository object instance with just an url." {
            val packageJson = PackageJson(
                gitHead = "bar",
                repository = Repository(
                    url = "git+ssh://example.com/a/b.git"
                )
            )

            parseVcsInfo(packageJson) shouldBe VcsInfo(
                VcsType.UNKNOWN,
                "git+ssh://example.com/a/b.git",
                "bar"
            )
        }
    }

    "splitNpmNamespaceAndName()" should {
        "return the namespace and name separately" {
            splitNamespaceAndName("@babel/core") shouldBe Pair("@babel", "core")
            splitNamespaceAndName("check-if-windows") shouldBe Pair("", "check-if-windows")
        }
    }
})
