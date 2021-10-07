/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.readValue

class BundlerTest : StringSpec({
    "createFromJson() parses JSON metadata for a Gem correctly" {
        val rspecGemJson = File("src/test/assets/bundler/rspec-3.7.0.yaml")

        val gemspec = GemSpec.createFromGem(rspecGemJson.readValue())

        gemspec shouldBe GemSpec(
            name = "rspec",
            version = "3.7.0",
            homepageUrl = "http://github.com/rspec",
            authors = sortedSetOf("Steven Baker", "David Chelimsky", "Myron Marston"),
            declaredLicenses = sortedSetOf("MIT"),
            description = "BDD for Ruby",
            runtimeDependencies = setOf("rspec-core", "rspec-expectations", "rspec-mocks"),
            vcs = VcsInfo(VcsType.GIT, "http://github.com/rspec/rspec.git", ""),
            artifact = RemoteArtifact(
                "https://rubygems.org/gems/rspec-3.7.0.gem",
                Hash("0174cfbed780e42aa181227af623e2ae37511f20a2fdfec48b54f6cf4d7a6404", HashAlgorithm.SHA256)
            )
        )
    }
})
