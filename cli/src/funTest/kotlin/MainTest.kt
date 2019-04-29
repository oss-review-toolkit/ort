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

package com.here.ort

import com.here.ort.downloader.VersionControlSystem
import com.here.ort.utils.normalizeVcsUrl
import com.here.ort.utils.safeDeleteRecursively
import com.here.ort.utils.test.patchActualResult
import com.here.ort.utils.test.patchExpectedResult

import io.kotlintest.TestCase
import io.kotlintest.TestResult
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

/**
 * A test for the main entry point of the application.
 */
class MainTest : StringSpec() {
    private val projectDir = File("../analyzer/src/funTest/assets/projects/synthetic")
    private val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()

    private lateinit var outputDir: File

    override fun beforeTest(testCase: TestCase) {
        outputDir = createTempDir()
    }

    override fun afterTest(testCase: TestCase, result: TestResult) {
        outputDir.safeDeleteRecursively(force = true)
    }

    init {
        "Activating only Gradle works" {
            val inputDir = File(projectDir, "gradle")

            val runResult = runMain(
                "analyze",
                "-m", "Gradle",
                "-i", inputDir.path,
                "-o", File(outputDir, "gradle").path
            )
            val iterator = runResult.stdout.iterator()
            while (iterator.hasNext()) {
                if (iterator.next() == "The following package managers are activated:") break
            }


            runResult.exitCode shouldBe 0
            iterator.hasNext() shouldBe true
            iterator.next() shouldBe "\tGradle"
        }

        "Activating only NPM works" {
            val inputDir = File(projectDir, "npm/package-lock")

            val runResult = runMain(
                "analyze",
                "-m", "NPM",
                "-i", inputDir.path,
                "-o", File(outputDir, "package-lock").path
            )
            val iterator = runResult.stdout.iterator()
            while (iterator.hasNext()) {
                if (iterator.next() == "The following package managers are activated:") break
            }

            runResult.exitCode shouldBe 0
            iterator.hasNext() shouldBe true
            iterator.next() shouldBe "\tNPM"
        }

        "Output formats are deduplicated" {
            val inputDir = File(projectDir, "gradle")

            val runResult = runMain(
                "analyze",
                "-m", "Gradle",
                "-i", inputDir.path,
                "-o", File(outputDir, "gradle").path,
                "-f", "json,yaml,json"
            )
            val lines = runResult.stdout.filter { it.startsWith("Writing analyzer result to ") }

            runResult.exitCode shouldBe 0
            lines.count() shouldBe 2
        }

        "Analyzer creates correct output" {
            val analyzerOutputDir = File(outputDir, "merged-results")
            val expectedResult = patchExpectedResult(
                File(projectDir, "gradle-all-dependencies-expected-result.yml"),
                url = vcsUrl,
                revision = vcsRevision,
                urlProcessed = normalizeVcsUrl(vcsUrl)
            )

            val runResult = runMain(
                "analyze",
                "-m", "Gradle",
                "-i", File(projectDir, "gradle").absolutePath,
                "-o", analyzerOutputDir.path
            )
            val analyzerResult = File(analyzerOutputDir, "analyzer-result.yml").readText()

            runResult.exitCode shouldBe 0
            patchActualResult(analyzerResult, patchStartAndEndTime = true) shouldBe expectedResult
        }

        "Package curation data file is applied correctly" {
            val analyzerOutputDir = File(outputDir, "curations")
            val expectedResult = patchExpectedResult(
                File(projectDir, "gradle-all-dependencies-expected-result-with-curations.yml"),
                url = vcsUrl,
                revision = vcsRevision,
                urlProcessed = normalizeVcsUrl(vcsUrl)
            )

            val runResult = runMain(
                "analyze",
                "-m", "Gradle",
                "-i", File(projectDir, "gradle").absolutePath,
                "-o", analyzerOutputDir.path,
                "--package-curations-file", File(projectDir, "gradle/curations.yml").toString()
            )
            val analyzerResult = File(analyzerOutputDir, "analyzer-result.yml").readText()

            runResult.exitCode shouldBe 0
            patchActualResult(analyzerResult, patchStartAndEndTime = true) shouldBe expectedResult
        }

        "Requirements are listed correctly" {
            val runResult = runMain("requirements")
            val errorLogs = runResult.stdout.find { it.contains(" ERROR - ") }

            errorLogs shouldBe null
            runResult.exitCode shouldBe 0
        }
    }

    private data class Result(val stdout: Sequence<String>, val exitCode: Int)

    private fun runMain(vararg args: String): Result {
        // Redirect standard output to a stream.
        val standardOut = System.out
        val streamOut = ByteArrayOutputStream()

        System.setOut(PrintStream(streamOut))

        try {
            val exitCode = Main.run(args.asList().toTypedArray())
            return Result(streamOut.toString().lineSequence(), exitCode)
        } finally {
            // Restore standard output.
            System.setOut(standardOut)
        }
    }
}
