/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

import com.fasterxml.jackson.module.kotlin.readValue

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.analyzer.Analyzer
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.downloader.vcs.Git
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.utils.core.normalizeVcsUrl
import org.ossreviewtoolkit.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import org.ossreviewtoolkit.utils.test.patchActualResultObject
import org.ossreviewtoolkit.utils.test.patchExpectedResult
import org.ossreviewtoolkit.utils.test.readOrtResult

class SbtFunTest : StringSpec({
    "Dependencies of the external 'sbt-multi-project-example' multi-project should be detected correctly" {
        val projectName = "sbt-multi-project-example"
        val projectDir = File("src/funTest/assets/projects/external/$projectName").absoluteFile
        val expectedOutputFile = projectDir.parentFile.resolve("$projectName-expected-output.yml")

        // Clean any previously generated POM files / target directories.
        Git().run(projectDir, "clean", "-fd")

        val ortResult = Analyzer(DEFAULT_ANALYZER_CONFIGURATION).run {
            analyze(findManagedFiles(projectDir, setOf(Sbt.Factory())))
        }

        val expectedResult = readOrtResult(expectedOutputFile)

        patchActualResultObject(ortResult, patchStartAndEndTime = true).withResolvedScopes() shouldBe expectedResult
    }

    "Dependencies of the synthetic 'http4s-template' project should be detected correctly" {
        val projectName = "sbt-http4s-template"
        val projectDir = File("src/funTest/assets/projects/synthetic/$projectName").absoluteFile
        val expectedOutputFile = projectDir.parentFile.resolve("$projectName-expected-output.yml")
        val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
        val vcsUrl = vcsDir.getRemoteUrl()
        val vcsRevision = vcsDir.getRevision()

        // Clean any previously generated POM files / target directories.
        Git().run(projectDir, "clean", "-fd")

        val ortResult = Analyzer(DEFAULT_ANALYZER_CONFIGURATION).run {
            analyze(findManagedFiles(projectDir, setOf(Sbt.Factory())))
        }

        val expectedResult = yamlMapper.readValue<OrtResult>(
            patchExpectedResult(
                expectedOutputFile,
                url = vcsUrl,
                revision = vcsRevision,
                urlProcessed = normalizeVcsUrl(vcsUrl)
            )
        )

        patchActualResultObject(ortResult, patchStartAndEndTime = true).withResolvedScopes() shouldBe expectedResult
    }
})
