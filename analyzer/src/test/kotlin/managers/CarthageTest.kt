/*
 * Copyright (C) 2020 Bosch.IO GmbH
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

package org.ossreviewtoolkit.analyzer.managers

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockkStatic

import java.io.File
import java.net.URL

import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import org.ossreviewtoolkit.utils.test.DEFAULT_REPOSITORY_CONFIGURATION
import org.ossreviewtoolkit.utils.test.USER_DIR

class CarthageTest : WordSpec() {
    private val carthage =
        Carthage("Carthage", USER_DIR, DEFAULT_ANALYZER_CONFIGURATION, DEFAULT_REPOSITORY_CONFIGURATION)

    init {
        "resolveDependencies" should {
            "parse a github dependency" {
                val cartfile = File("src/test/assets/carthage/Cartfile-github.resolved")

                val result = carthage.resolveDependencies(cartfile, emptyMap()).single()

                with(result.packages) {
                    size shouldBe 1
                    single().apply {
                        id.type shouldBe "Carthage"
                        vcs.url shouldBe "https://github.com/Alamofire/AlamofireImage.git"
                        vcs.revision shouldBe "3.2.0"
                    }
                }
            }

            "parse a generic git dependency" {
                val cartfile = File("src/test/assets/carthage/Cartfile-generic-git.resolved")

                val result = carthage.resolveDependencies(cartfile, emptyMap()).single()

                with(result.packages) {
                    size shouldBe 1
                    single().apply {
                        id.type shouldBe "Carthage"
                        vcs.type shouldBe VcsType.GIT
                        vcs.url shouldBe "https://host.tld/path/to/project.git"
                        vcs.revision shouldBe "1.0.0"
                    }
                }
            }

            "parse a binary dependency url" {
                mockkStatic("kotlin.io.TextStreamsKt")
                every { URL("https://host.tld/path/to/binary/spec.json").readBytes() } returns
                        File("src/test/assets/carthage/Carthage-binary-specification.json").readText().toByteArray()

                val cartfile = File("src/test/assets/carthage/Cartfile-binary.resolved")

                val result = carthage.resolveDependencies(cartfile, emptyMap()).single()
                with(result.packages) {
                    size shouldBe 1
                    single().apply {
                        id.type shouldBe "Carthage"
                        id.name shouldBe "spec"
                        binaryArtifact.url shouldBe "https://host.tld/path/to/binary/dependency.zip"
                    }
                }
            }

            "parse mixed dependencies" {
                mockkStatic("kotlin.io.TextStreamsKt")
                every { URL("https://host.tld/path/to/binary/spec.json").readBytes() } returns
                        File("src/test/assets/carthage/Carthage-binary-specification.json").readText().toByteArray()

                val cartfile = File("src/test/assets/carthage/Cartfile-mixed.resolved")

                val result = carthage.resolveDependencies(cartfile, emptyMap()).single()

                with(result.packages) {
                    size shouldBe 3
                    forEach {
                        it.id.type shouldBe "Carthage"
                    }
                    count { "user/project" in it.vcs.url } shouldBe 1
                    count { "user-2/project_2" in it.vcs.url } shouldBe 1
                    count { "binary/dependency.zip" in it.binaryArtifact.url } shouldBe 1
                }
            }

            "throw an error for a wrongly defined dependency" {
                val cartfile = File("src/test/assets/carthage/Cartfile-faulty.resolved")

                shouldThrow<IllegalArgumentException> {
                    carthage.resolveDependencies(cartfile, emptyMap())
                }
            }
        }
    }
}
