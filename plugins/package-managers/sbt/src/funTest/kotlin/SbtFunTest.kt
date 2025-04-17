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

package org.ossreviewtoolkit.plugins.packagemanagers.sbt

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.analyzer.analyze
import org.ossreviewtoolkit.analyzer.getAnalyzerResult
import org.ossreviewtoolkit.model.config.PackageManagerConfiguration
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.plugins.versioncontrolsystems.git.GitCommand
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.matchExpectedResult
import org.ossreviewtoolkit.utils.test.patchActualResult

class SbtFunTest : StringSpec({
    "Dependencies of the external 'multi-project' should be detected correctly" {
        val definitionFile = getAssetFile("projects/external/multi-project/build.sbt")
        val expectedResultFile = getAssetFile("projects/external/multi-project-expected-output.yml")
        val expectedResult = matchExpectedResult(expectedResultFile, definitionFile)

        // Clean any previously generated POM files / target directories.
        GitCommand.run(definitionFile.parentFile, "clean", "-fd").requireSuccess()

        val result = analyze(
            definitionFile.parentFile,
            packageManagers = setOf(SbtFactory()),
            packageManagerConfiguration = mapOf(
                "SBT" to PackageManagerConfiguration(options = mapOf("javaVersion" to "11"))
            )
        ).getAnalyzerResult()

        result.toYaml() shouldBe expectedResult
    }

    "Dependencies of the synthetic 'http4s-template' project should be detected correctly" {
        val definitionFile = getAssetFile("projects/synthetic/http4s-template/build.sbt")
        val expectedResultFile = getAssetFile("projects/synthetic/http4s-template-expected-output.yml")
        val expectedResult = matchExpectedResult(expectedResultFile, definitionFile)

        // Clean any previously generated POM files / target directories.
        GitCommand.run(definitionFile.parentFile, "clean", "-fd").requireSuccess()

        val result = analyze(
            definitionFile.parentFile,
            packageManagers = setOf(SbtFactory()),
            packageManagerConfiguration = mapOf(
                "SBT" to PackageManagerConfiguration(options = mapOf("javaVersion" to "11"))
            )
        ).getAnalyzerResult()

        patchActualResult(result.toYaml()) shouldBe expectedResult
    }
})
