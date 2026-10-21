/*
 * Copyright (C) 2017 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.plugins.packagemanagers.node.PackageJson.Distribution
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

    "parsePackage()" should {
        "prefer the ModuleInfoResolver's download URL and hash over the package.json's private fields while " +
            "preferring the package.json's description, homepage and VCS info" {
                // This simulates a `package.json` with unreliable "_resolved" / "_integrity" fields that leaked
                // from the publisher's local environment into the published package.
                val packageJson = PackageJson(
                    name = "jail-monkey",
                    version = "3.0.0",
                    description = "A description",
                    homepage = "https://example.com/jail-monkey",
                    resolved = "https://registry.npmjs.org/jail-monkey/-/jail-monkey-3.0.0.tgz",
                    integrity = "sha512-O76UCnkijUPGQooYRD3Rq/07JdPc5/p5hetLMKPNaTz2S+7li3dT7yPXs362NbIS/" +
                        "BZA22/ffPPE4+hwv0fYLA==",
                    repository = Repository(type = "Git", url = "https://github.com/wrong/jail-monkey.git")
                )

                val moduleInfoResolver = mockk<ModuleInfoResolver>()
                every { moduleInfoResolver.getModuleInfo("jail-monkey@3.0.0") } returns PackageJson(
                    name = "jail-monkey",
                    version = "3.0.0",
                    description = "From the registry",
                    homepage = "https://example.com/artifactory/jail-monkey",
                    repository = Repository(type = "Git", url = "https://example.com/artifactory/jail-monkey.git"),
                    dist = Distribution(
                        tarball = "https://example.com/artifactory/jail-monkey/-/jail-monkey-3.0.0.tgz",
                        shasum = "82559b2192176b994c53bab14cb7bb93b2dfbe15"
                    )
                )

                val result = parsePackage(packageJson, moduleInfoResolver)

                // Unlike the download URL and hash, the description, homepage and VCS info are regular public
                // fields that are not prone to leaking stale data, so the data from `package.json` is preferred
                // here, even though the ModuleInfoResolver was queried and returned different data.
                result.description shouldBe "A description"
                result.homepageUrl shouldBe "https://example.com/jail-monkey"
                result.sourceArtifact shouldBe RemoteArtifact(
                    url = "https://example.com/artifactory/jail-monkey/-/jail-monkey-3.0.0.tgz",
                    hash = Hash.create("82559b2192176b994c53bab14cb7bb93b2dfbe15")
                )
                result.vcs shouldBe VcsInfo(
                    type = VcsType.GIT,
                    url = "https://github.com/wrong/jail-monkey.git",
                    revision = ""
                )
            }

        "fall back to the package.json's own data if the ModuleInfoResolver returns no data" {
            val packageJson = PackageJson(
                name = "jail-monkey",
                version = "3.0.0",
                description = "A description",
                homepage = "https://example.com/jail-monkey",
                resolved = "https://registry.example.com/jail-monkey/-/jail-monkey-3.0.0.tgz",
                integrity = "sha512-ETo7x4PYUfwDcyFLGep76fo95UHsuf4CbVLGA+jqGcF0zA6XBfi5DTEiEsDDpthFPd+" +
                    "z4xQUCc9L7cjvAzWQtA=="
            )

            val moduleInfoResolver = mockk<ModuleInfoResolver>()
            every { moduleInfoResolver.getModuleInfo("jail-monkey@3.0.0") } returns null

            val result = parsePackage(packageJson, moduleInfoResolver)

            result.description shouldBe "A description"
            result.homepageUrl shouldBe "https://example.com/jail-monkey"
            result.sourceArtifact shouldBe RemoteArtifact(
                url = "https://registry.example.com/jail-monkey/-/jail-monkey-3.0.0.tgz",
                hash = Hash.create(
                    "sha512-ETo7x4PYUfwDcyFLGep76fo95UHsuf4CbVLGA+jqGcF0zA6XBfi5DTEiEsDDpthFPd+z4xQUCc9L7cjvAzWQtA=="
                )
            )
        }

        "merge the VCS info from the package.json with the one from the ModuleInfoResolver if the package.json's " +
            "VCS info is incomplete" {
                val packageJson = PackageJson(
                    name = "jail-monkey",
                    version = "3.0.0"
                )

                val moduleInfoResolver = mockk<ModuleInfoResolver>()
                every { moduleInfoResolver.getModuleInfo("jail-monkey@3.0.0") } returns PackageJson(
                    name = "jail-monkey",
                    version = "3.0.0",
                    repository = Repository(type = "Git", url = "git+https://example.com/jail-monkey.git")
                )

                val result = parsePackage(packageJson, moduleInfoResolver)

                result.vcs shouldBe VcsInfo(
                    type = VcsType.GIT,
                    url = "git+https://example.com/jail-monkey.git",
                    revision = ""
                )
            }

        "not query the ModuleInfoResolver at all and preserve the package.json's own source artifact info if " +
            "isRegistryDependency is false" {
                val packageJson = PackageJson(
                    name = "jail-monkey",
                    version = "3.0.0",
                    description = "A description",
                    homepage = "https://example.com/jail-monkey",
                    resolved = "file:../fork/jail-monkey"
                )

                val moduleInfoResolver = mockk<ModuleInfoResolver>()

                // This simulates a fork that kept the same name and version as the original published package,
                // which could lead to the resolver returning unrelated metadata if it were queried.
                every { moduleInfoResolver.getModuleInfo("jail-monkey@3.0.0") } returns PackageJson(
                    name = "jail-monkey",
                    version = "3.0.0",
                    dist = Distribution(
                        tarball = "https://registry.npmjs.org/jail-monkey/-/jail-monkey-3.0.0.tgz",
                        shasum = "82559b2192176b994c53bab14cb7bb93b2dfbe15"
                    )
                )

                val result = parsePackage(packageJson, moduleInfoResolver, isRegistryDependency = false)

                verify(exactly = 0) { moduleInfoResolver.getModuleInfo(any()) }

                result.description shouldBe "A description"
                result.homepageUrl shouldBe "https://example.com/jail-monkey"
                result.sourceArtifact shouldBe RemoteArtifact(
                    url = "file:../fork/jail-monkey",
                    hash = Hash.NONE
                )
            }
    }

    "isGitOrLocalResolution()" should {
        "return false for a null resolved URI" {
            isGitOrLocalResolution(null) shouldBe false
        }

        "return false for a registry tarball URL" {
            isGitOrLocalResolution("https://registry.npmjs.org/jail-monkey/-/jail-monkey-3.0.0.tgz") shouldBe false
        }

        "return true for Git and local resolutions" {
            val resolutions = listOf(
                "git+https://github.com/fork-owner/jail-monkey.git#0123456789abcdef0123456789abcdef01234567",
                "github:fork-owner/jail-monkey#0123456789abcdef0123456789abcdef01234567",
                "gitlab:fork-owner/jail-monkey#0123456789abcdef0123456789abcdef01234567",
                "bitbucket:fork-owner/jail-monkey#0123456789abcdef0123456789abcdef01234567",
                "file:../local-package",
                "link:../local-package"
            )

            resolutions.forAll { resolved ->
                isGitOrLocalResolution(resolved) shouldBe true
            }
        }
    }
})
