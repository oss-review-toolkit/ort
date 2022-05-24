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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.utils.ort.normalizeVcsUrl
import org.ossreviewtoolkit.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import org.ossreviewtoolkit.utils.test.DEFAULT_REPOSITORY_CONFIGURATION
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.patchExpectedResult

class CarthageFunTest : StringSpec() {
    private val projectDir = File("src/funTest/assets/projects/synthetic/carthage").absoluteFile
    private val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()

    private val normalizedVcsUrl = normalizeVcsUrl(vcsUrl)
    private val gitHubProject = normalizedVcsUrl.split('/', '.').dropLast(1).takeLast(2).joinToString(":")

    init {
        "Project dependencies are detected correctly" {
            val cartfileResolved = projectDir.resolve("Cartfile.resolved")
            val vcsPath = vcsDir.getPathToRoot(projectDir)
            val expectedResult = patchExpectedResult(
                projectDir.parentFile.resolve("carthage-expected-output.yml"),
                definitionFilePath = "$vcsPath/Cartfile.resolved",
                path = vcsPath,
                revision = vcsRevision,
                url = normalizedVcsUrl,
                custom = mapOf("<REPLACE_GITHUB_PROJECT>" to gitHubProject)
            )

            val result = createCarthage().resolveSingleProject(cartfileResolved)

            result.toYaml() shouldBe expectedResult
        }
    }

    private fun createCarthage() =
        Carthage("Carthage", USER_DIR, DEFAULT_ANALYZER_CONFIGURATION, DEFAULT_REPOSITORY_CONFIGURATION)
}
