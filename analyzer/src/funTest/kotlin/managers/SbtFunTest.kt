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

package org.ossreviewtoolkit.analyzer.managers

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.analyzer.Analyzer
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.downloader.vcs.Git
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.utils.ort.normalizeVcsUrl
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.patchActualResult
import org.ossreviewtoolkit.utils.test.patchExpectedResult

class SbtFunTest : StringSpec({
    "Dependencies of the external 'sbt-multi-project-example' multi-project should be detected correctly" {
        val projectDir = getAssetFile("projects/external/sbt-multi-project-example")
        val expectedResult = patchExpectedResult(
            projectDir.resolveSibling("sbt-multi-project-example-expected-output.yml")
        )

        // Clean any previously generated POM files / target directories.
        Git().run(projectDir, "clean", "-fd")

        val ortResult = Analyzer(AnalyzerConfiguration()).run {
            analyze(findManagedFiles(projectDir, setOf(Sbt.Factory())))
        }

        patchActualResult(ortResult.withResolvedScopes(), patchStartAndEndTime = true) shouldBe expectedResult
    }

    "Dependencies of the synthetic 'http4s-template' project should be detected correctly" {
        val projectDir = getAssetFile("projects/synthetic/sbt-http4s-template")
        val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
        val vcsUrl = vcsDir.getRemoteUrl()
        val vcsRevision = vcsDir.getRevision()
        val expectedResult = patchExpectedResult(
            projectDir.resolveSibling("sbt-http4s-template-expected-output.yml"),
            url = vcsUrl,
            revision = vcsRevision,
            urlProcessed = normalizeVcsUrl(vcsUrl)
        )

        // Clean any previously generated POM files / target directories.
        Git().run(projectDir, "clean", "-fd")

        val ortResult = Analyzer(AnalyzerConfiguration()).run {
            analyze(findManagedFiles(projectDir, setOf(Sbt.Factory())))
        }

        patchActualResult(ortResult.withResolvedScopes(), patchStartAndEndTime = true) shouldBe expectedResult
    }
})
