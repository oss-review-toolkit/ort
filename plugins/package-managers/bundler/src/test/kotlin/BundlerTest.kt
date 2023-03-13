/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.bundler

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.utils.test.createTestTempDir

class BundlerTest : WordSpec({
    "parseBundlerVersionFromLockfile()" should {
        "correctly parse the Bundler version" {
            val lockfile = createTestTempDir().resolve(BUNDLER_LOCKFILE_NAME).apply {
                writeText(
                    """
                    GEM
                      remote: https://rubygems.org/
                      specs:
                        zeitwerk (2.6.0)

                    PLATFORMS
                      ruby

                    BUNDLED WITH
                       2.3.20
                    """.trimIndent()
                )
            }

            parseBundlerVersionFromLockfile(lockfile) shouldBe "2.3.20"
        }
    }

    "createFromJson()" should {
        "parse JSON metadata for a Gem correctly" {
            val rspecGemJson = File("src/test/assets/rspec-3.7.0.yaml")

            val gemspec = GemSpec.createFromGem(rspecGemJson.readValue())

            gemspec shouldBe GemSpec(
                name = "rspec",
                version = "3.7.0",
                homepageUrl = "http://github.com/rspec",
                authors = setOf("Steven Baker", "David Chelimsky", "Myron Marston"),
                declaredLicenses = setOf("MIT"),
                description = "BDD for Ruby",
                runtimeDependencies = setOf("rspec-core", "rspec-expectations", "rspec-mocks"),
                vcs = VcsInfo(VcsType.GIT, "http://github.com/rspec/rspec.git", ""),
                artifact = RemoteArtifact(
                    "https://rubygems.org/gems/rspec-3.7.0.gem",
                    Hash("0174cfbed780e42aa181227af623e2ae37511f20a2fdfec48b54f6cf4d7a6404", HashAlgorithm.SHA256)
                )
            )
        }
    }
})
