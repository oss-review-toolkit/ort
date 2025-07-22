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

package org.ossreviewtoolkit.utils.ort

import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe

import java.nio.file.Paths

class VcsUtilsTest : WordSpec({
    "normalizeVcsUrl" should {
        "do nothing for empty URLs" {
            normalizeVcsUrl("") shouldBe ""
        }

        "handle anonymous Git / HTTPS URL schemes" {
            val packages = mapOf(
                "git://github.com/cheeriojs/cheerio.git"
                    to "https://github.com/cheeriojs/cheerio.git",
                "git+https://github.com/fb55/boolbase.git"
                    to "https://github.com/fb55/boolbase.git",
                "https://www.github.com/DefinitelyTyped/DefinitelyTyped.git"
                    to "https://github.com/DefinitelyTyped/DefinitelyTyped.git"
            )

            packages.entries.forAll { (actualUrl, expectedUrl) ->
                normalizeVcsUrl(actualUrl) shouldBe expectedUrl
            }
        }

        "handle authenticated SSH URL schemes" {
            val packages = mapOf(
                "git+ssh://git@github.com/logicalparadox/idris.git"
                    to "ssh://git@github.com/logicalparadox/idris.git",
                "git@github.com:oss-review-toolkit/ort.git"
                    to "ssh://git@github.com/oss-review-toolkit/ort.git",
                "ssh://user@gerrit.server.com:29418/parent/project"
                    to "ssh://user@gerrit.server.com:29418/parent/project"
            )

            packages.entries.forAll { (actualUrl, expectedUrl) ->
                normalizeVcsUrl(actualUrl) shouldBe expectedUrl
            }
        }

        "add missing https:// for GitHub URLs" {
            val packages = mapOf(
                "github.com/leanovate/play-mockws"
                    to "https://github.com/leanovate/play-mockws.git"
            )

            packages.entries.forAll { (actualUrl, expectedUrl) ->
                normalizeVcsUrl(actualUrl) shouldBe expectedUrl
            }
        }

        "add missing .git for GitHub URLs" {
            val packages = mapOf(
                "https://github.com/fb55/nth-check"
                    to "https://github.com/fb55/nth-check.git",
                "git://github.com/isaacs/inherits"
                    to "https://github.com/isaacs/inherits.git"
            )

            packages.entries.forAll { (actualUrl, expectedUrl) ->
                normalizeVcsUrl(actualUrl) shouldBe expectedUrl
            }
        }

        "add missing git@ for GitHub SSH URLs" {
            val packages = mapOf(
                "ssh://github.com/heremaps/here-aaa-java-sdk.git"
                    to "ssh://git@github.com/heremaps/here-aaa-java-sdk.git",
                "ssh://github.com/heremaps/here-aaa-java-sdk"
                    to "ssh://git@github.com/heremaps/here-aaa-java-sdk.git"
            )

            packages.entries.forAll { (actualUrl, expectedUrl) ->
                normalizeVcsUrl(actualUrl) shouldBe expectedUrl
            }
        }

        "handle a trailing slash correctly" {
            val packages = mapOf(
                "https://github.com/kilian/electron-to-chromium/"
                    to "https://github.com/kilian/electron-to-chromium.git",
                "git://github.com/isaacs/inherits.git/"
                    to "https://github.com/isaacs/inherits.git"
            )

            packages.entries.forAll { (actualUrl, expectedUrl) ->
                normalizeVcsUrl(actualUrl) shouldBe expectedUrl
            }
        }

        "handle file schemes correctly" {
            var userDir = System.getProperty("user.dir").replace("\\", "/")
            var userRoot = Paths.get(userDir).root.toString().replace("\\", "/")

            if (!userDir.startsWith("/")) {
                userDir = "/$userDir"
            }

            if (!userRoot.startsWith("/")) {
                userRoot = "/$userRoot"
            }

            val packages = mapOf(
                "relative/path/to/local/file"
                    to "file://$userDir/relative/path/to/local/file",
                "relative/path/to/local/dir/"
                    to "file://$userDir/relative/path/to/local/dir",
                "/absolute/path/to/local/file"
                    to "file://${userRoot}absolute/path/to/local/file",
                "/absolute/path/to/local/dir/"
                    to "file://${userRoot}absolute/path/to/local/dir"
            )

            packages.entries.forAll { (actualUrl, expectedUrl) ->
                normalizeVcsUrl(actualUrl) shouldBe expectedUrl
            }
        }

        "convert git to https for GitHub URLs" {
            val packages = mapOf(
                "git://github.com:ccavanaugh/jgnash.git"
                    to "https://github.com/ccavanaugh/jgnash.git",
                "git://github.com/netty/netty.git/netty-buffer"
                    to "https://github.com/netty/netty.git/netty-buffer"
            )

            packages.entries.forAll { (actualUrl, expectedUrl) ->
                normalizeVcsUrl(actualUrl) shouldBe expectedUrl
            }
        }

        "not strip svn+ prefix from Subversion URLs" {
            val packages = mapOf(
                "svn+ssh://svn.code.sf.net/p/stddecimal/code/trunk"
                    to "svn+ssh://svn.code.sf.net/p/stddecimal/code/trunk"
            )

            packages.entries.forAll { (actualUrl, expectedUrl) ->
                normalizeVcsUrl(actualUrl) shouldBe expectedUrl
            }
        }

        "fixup crazy URLs" {
            val packages = mapOf(
                "git@github.com/cisco/node-jose.git"
                    to "ssh://git@github.com/cisco/node-jose.git",
                "https://git@github.com:hacksparrow/node-easyimage.git"
                    to "https://github.com/hacksparrow/node-easyimage.git"
            )

            packages.entries.forAll { (actualUrl, expectedUrl) ->
                normalizeVcsUrl(actualUrl) shouldBe expectedUrl
            }
        }

        "keep the query component" {
            val url = "https://github.com/oss-review-toolkit/ort-test-data-git-repo.git?manifest=manifest.xml"

            normalizeVcsUrl(url) shouldBe url
        }
    }
})
