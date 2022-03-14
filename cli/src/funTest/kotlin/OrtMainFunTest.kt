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

package org.ossreviewtoolkit.cli

import com.github.ajalt.clikt.core.MutuallyExclusiveGroupException
import com.github.ajalt.clikt.core.ProgramResult

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.utils.common.redirectStdout
import org.ossreviewtoolkit.utils.core.normalizeVcsUrl
import org.ossreviewtoolkit.utils.test.createTestTempDir
import org.ossreviewtoolkit.utils.test.patchActualResult
import org.ossreviewtoolkit.utils.test.patchExpectedResult

/**
 * A test for the main entry point of the application.
 */
class OrtMainFunTest : StringSpec() {
    private val projectDir = File("../analyzer/src/funTest/assets/projects/synthetic")
    private val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()

    private lateinit var outputDir: File

    override suspend fun beforeTest(testCase: TestCase) {
        outputDir = createTestTempDir()
    }

    init {
        "Activating only Gradle works" {
            val inputDir = projectDir.resolve("gradle")

            val stdout = runMain(
                "analyze",
                "-m", "Gradle",
                "-i", inputDir.path,
                "-o", outputDir.resolve("gradle").path
            )
            val iterator = stdout.iterator()
            while (iterator.hasNext()) {
                if (iterator.next() == "The following package managers are activated:") break
            }

            iterator.hasNext() shouldBe true
            iterator.next() shouldBe "\tGradle"
        }

        "Activating only NPM works" {
            val inputDir = projectDir.resolve("npm/package-lock")

            val stdout = runMain(
                "analyze",
                "-m", "NPM",
                "-i", inputDir.path,
                "-o", outputDir.resolve("package-lock").path
            )
            val iterator = stdout.iterator()
            while (iterator.hasNext()) {
                if (iterator.next() == "The following package managers are activated:") break
            }

            iterator.hasNext() shouldBe true
            iterator.next() shouldBe "\tNPM"
        }

        "Output formats are deduplicated" {
            val inputDir = projectDir.resolve("gradle")

            val stdout = runMain(
                "analyze",
                "-m", "Gradle",
                "-i", inputDir.path,
                "-o", outputDir.resolve("gradle").path,
                "-f", "json,yaml,json"
            )
            val lines = stdout.filter { it.startsWith("Writing analyzer result to ") }

            lines.count() shouldBe 2
        }

        "Analyzer creates correct output" {
            val analyzerOutputDir = outputDir.resolve("merged-results")
            val expectedResult = patchExpectedResult(
                projectDir.resolve("gradle-all-dependencies-expected-result.yml"),
                url = vcsUrl,
                revision = vcsRevision,
                urlProcessed = normalizeVcsUrl(vcsUrl)
            )

            runMain(
                "analyze",
                "-m", "Gradle",
                "-i", projectDir.resolve("gradle").absolutePath,
                "-o", analyzerOutputDir.path
            )

            val analyzerResult = analyzerOutputDir.resolve("analyzer-result.yml").readValue<OrtResult>()
            val resolvedResult = analyzerResult.withResolvedScopes()

            patchActualResult(resolvedResult, patchStartAndEndTime = true) shouldBe expectedResult
        }

        "Package curation data file is applied correctly" {
            val analyzerOutputDir = outputDir.resolve("curations")
            val expectedResult = patchExpectedResult(
                projectDir.resolve("gradle-all-dependencies-expected-result-with-curations.yml"),
                url = vcsUrl,
                revision = vcsRevision,
                urlProcessed = normalizeVcsUrl(vcsUrl)
            )

            runMain(
                "analyze",
                "-m", "Gradle",
                "-i", projectDir.resolve("gradle").absolutePath,
                "-o", analyzerOutputDir.path,
                "--package-curations-file", projectDir.resolve("gradle/curations.yml").toString()
            )

            val analyzerResult = analyzerOutputDir.resolve("analyzer-result.yml").readValue<OrtResult>()
            val resolvedResult = analyzerResult.withResolvedScopes()

            patchActualResult(resolvedResult, patchStartAndEndTime = true) shouldBe expectedResult
        }

        "Passing mutually exclusive evaluator options fails" {
            shouldThrow<MutuallyExclusiveGroupException> {
                runMain(
                    "evaluate",
                    "-i", "build.gradle.kts",
                    "--rules-file", "build.gradle.kts",
                    "--rules-resource", "DUMMY"
                )
            }
        }
    }

    private fun runMain(vararg args: String) =
        redirectStdout {
            @Suppress("SwallowedException")
            try {
                OrtMain().parse(args.asList())
            } catch (e: ProgramResult) {
                // Ignore exceptions that just propagate the program result.
            }
        }.lineSequence()
}
