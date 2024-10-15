/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.pub

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.plugins.packagemanagers.pub.Pubspec.GitDependency
import org.ossreviewtoolkit.plugins.packagemanagers.pub.Pubspec.HostedDependency
import org.ossreviewtoolkit.plugins.packagemanagers.pub.Pubspec.PathDependency
import org.ossreviewtoolkit.plugins.packagemanagers.pub.Pubspec.SdkDependency

class PubspecTest : WordSpec({
    "parsePubspec()" should {
        "parse an explicit null map" {
            val yaml = """
                name: some-package
                dependencies:
            """.trimIndent()

            parsePubspec(yaml).dependencies should beNull()
        }

        "parse hosted dependencies" {
            val yaml = """
                name: some-package
                dependencies:
                  pkg-1: ^1.0.0
                  pkg-2:
                    hosted: https://some-package-server.com
                    version: ^1.4.0
                  pkg-3:
                    hosted:
                      name: pkg-3
                      url: https://some-other-package-server.com
                    version: ^1.4.0  
            """.trimIndent()

            parsePubspec(yaml).dependencies shouldBe mapOf(
                "pkg-1" to HostedDependency(
                    version = "^1.0.0"
                ),
                "pkg-2" to HostedDependency(
                    hosted = "https://some-package-server.com",
                    version = "^1.4.0"
                ),
                "pkg-3" to HostedDependency(
                    hosted = "https://some-other-package-server.com",
                    version = "^1.4.0"
                )
            )
        }

        "parse SDK dependencies" {
            val yaml = """
                name: some-package
                dependencies:
                  flutter_driver:
                    sdk: flutter
            """.trimIndent()

            parsePubspec(yaml).dependencies shouldBe mapOf(
                "flutter_driver" to SdkDependency(
                    sdk = "flutter"
                )
            )
        }

        "parse path dependencies" {
            val yaml = """
                name: some-package
                dependencies:
                  transmogrify:
                    path: /Users/me/transmogrify
            """.trimIndent()

            parsePubspec(yaml).dependencies shouldBe mapOf(
                "transmogrify" to PathDependency(
                    path = "/Users/me/transmogrify"
                )
            )
        }

        "parse git dependencies" {
            val yaml = """
                name: some-package
                dependencies:
                  pkg-1:
                    git: git@github.com:some-org/some-project.git
                  pkg-2:
                    git:
                      url: git@github.com:some-org/some-project.git
                      path: path/to/pkg-2
                  pkg-3:
                    git:
                      url: git@github.com:some-org/some-project.git
                      ref: some-branch
            """.trimIndent()

            parsePubspec(yaml).dependencies shouldBe mapOf(
                "pkg-1" to GitDependency(
                    url = "git@github.com:some-org/some-project.git"
                ),
                "pkg-2" to GitDependency(
                    url = "git@github.com:some-org/some-project.git",
                    path = "path/to/pkg-2"
                ),
                "pkg-3" to GitDependency(
                    url = "git@github.com:some-org/some-project.git",
                    ref = "some-branch"
                )
            )
        }
    }
})
