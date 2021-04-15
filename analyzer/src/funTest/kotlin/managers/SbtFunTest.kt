/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

import org.ossreviewtoolkit.analyzer.Analyzer
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.downloader.vcs.Git
import org.ossreviewtoolkit.utils.Ci
import org.ossreviewtoolkit.utils.normalizeVcsUrl
import org.ossreviewtoolkit.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import org.ossreviewtoolkit.utils.test.patchActualResult
import org.ossreviewtoolkit.utils.test.patchExpectedResult

class SbtFunTest : StringSpec({
    "Dependencies of the external 'directories' single project should be detected correctly".config(
        enabled = !Ci.isAzureWindows // Disabled as a prompt in Sbt 1.5.0 blocks execution when getting the version.
    ) {
        val projectName = "directories"
        val projectDir = File("src/funTest/assets/projects/external/$projectName").absoluteFile
        val expectedOutputFile = projectDir.resolveSibling("$projectName-expected-output.yml")

        // Clean any previously generated POM files / target directories.
        Git().run(projectDir, "clean", "-fd")

        val ortResult = Analyzer(DEFAULT_ANALYZER_CONFIGURATION).analyze(projectDir, listOf(Sbt.Factory()))

        val actualResult = ortResult.withResolvedScopes().toYaml()
        val expectedResult = patchExpectedResult(expectedOutputFile)

        patchActualResult(actualResult, patchStartAndEndTime = true) shouldBe expectedResult
    }

    "Dependencies of the external 'sbt-multi-project-example' multi-project should be detected correctly".config(
        enabled = !Ci.isAzureWindows // Disabled as a prompt in Sbt 1.5.0 blocks execution when getting the version.
    ) {
        val projectName = "sbt-multi-project-example"
        val projectDir = File("src/funTest/assets/projects/external/$projectName").absoluteFile
        val expectedOutputFile = projectDir.parentFile.resolve("$projectName-expected-output.yml")

        // Clean any previously generated POM files / target directories.
        Git().run(projectDir, "clean", "-fd")

        val ortResult = Analyzer(DEFAULT_ANALYZER_CONFIGURATION).analyze(projectDir, listOf(Sbt.Factory()))

        val actualResult = ortResult.withResolvedScopes().toYaml()
        val expectedResult = patchExpectedResult(expectedOutputFile)

        patchActualResult(actualResult, patchStartAndEndTime = true) shouldBe expectedResult
    }

    "Dependencies of the synthetic 'http4s-template' project should be detected correctly".config(
        enabled = !Ci.isAzureWindows // Disabled as a prompt in Sbt 1.5.0 blocks execution when getting the version.
    ) {
        val projectName = "sbt-http4s-template"
        val projectDir = File("src/funTest/assets/projects/synthetic/$projectName").absoluteFile
        val expectedOutputFile = projectDir.parentFile.resolve("$projectName-expected-output.yml")
        val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
        val vcsUrl = vcsDir.getRemoteUrl()
        val vcsRevision = vcsDir.getRevision()

        // Clean any previously generated POM files / target directories.
        Git().run(projectDir, "clean", "-fd")

        val ortResult = Analyzer(DEFAULT_ANALYZER_CONFIGURATION).analyze(projectDir, listOf(Sbt.Factory()))

        val actualResult = ortResult.withResolvedScopes().toYaml()
        val expectedResult = patchExpectedResult(
            expectedOutputFile,
            url = vcsUrl,
            revision = vcsRevision,
            urlProcessed = normalizeVcsUrl(vcsUrl)
        )

        patchActualResult(actualResult, patchStartAndEndTime = true) shouldBe expectedResult
    }
})
