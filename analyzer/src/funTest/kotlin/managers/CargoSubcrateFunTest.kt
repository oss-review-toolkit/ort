/*
 * Copyright (C) 2019 HERE Europe B.V.
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

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.utils.ort.normalizeVcsUrl
import org.ossreviewtoolkit.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import org.ossreviewtoolkit.utils.test.DEFAULT_REPOSITORY_CONFIGURATION
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.patchExpectedResult

class CargoSubcrateFunTest : StringSpec() {
    private val projectDir = File("src/funTest/assets/projects/synthetic/cargo-subcrate").absoluteFile
    private val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()

    init {
        "Lib project dependencies are detected correctly" {
            val packageFile = projectDir.resolve("Cargo.toml")
            val vcsPath = vcsDir.getPathToRoot(projectDir)
            val expectedResult = patchExpectedResult(
                projectDir.parentFile.resolve("cargo-subcrate-lib-expected-output.yml"),
                definitionFilePath = "$vcsPath/Cargo.toml",
                path = vcsPath,
                revision = vcsRevision,
                url = normalizeVcsUrl(vcsUrl)
            )

            val result = createCargo().resolveSingleProject(packageFile)

            result.toYaml() shouldBe expectedResult
        }

        "Integration sub-project dependencies are detected correctly" {
            val integrationProjectDir = projectDir.resolve("integration")
            val packageFile = integrationProjectDir.resolve("Cargo.toml")
            val vcsPath = vcsDir.getPathToRoot(integrationProjectDir)
            val expectedResult = patchExpectedResult(
                projectDir.parentFile.resolve("cargo-subcrate-integration-expected-output.yml"),
                definitionFilePath = "$vcsPath/Cargo.toml",
                path = vcsPath,
                revision = vcsRevision,
                url = normalizeVcsUrl(vcsUrl)
            )

            val result = createCargo().resolveSingleProject(packageFile)

            result.toYaml() shouldBe expectedResult
        }

        "Client sub-project dependencies are detected correctly" {
            val clientProjectDir = projectDir.resolve("client")
            val packageFile = clientProjectDir.resolve("Cargo.toml")
            val vcsPath = vcsDir.getPathToRoot(clientProjectDir)
            val expectedResult = patchExpectedResult(
                projectDir.parentFile.resolve("cargo-subcrate-client-expected-output.yml"),
                definitionFilePath = "$vcsPath/Cargo.toml",
                path = vcsPath,
                revision = vcsRevision,
                url = normalizeVcsUrl(vcsUrl)
            )

            val result = createCargo().resolveSingleProject(packageFile)

            result.toYaml() shouldBe expectedResult
        }
    }

    private fun createCargo() =
        Cargo("Cargo", USER_DIR, DEFAULT_ANALYZER_CONFIGURATION, DEFAULT_REPOSITORY_CONFIGURATION)
}
