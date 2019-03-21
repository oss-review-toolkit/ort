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

            // Redirect standard output to a stream.
            val standardOut = System.out
            val streamOut = ByteArrayOutputStream()
            System.setOut(PrintStream(streamOut))

            try {
                val exitCode = Main.run(arrayOf(
                        "analyze",
                        "-m", "Gradle",
                        "-i", inputDir.path,
                        "-o", File(outputDir, "gradle").path
                ))

                exitCode shouldBe 0

                val lines = streamOut.toString().lineSequence().iterator()
                while (lines.hasNext()) {
                    if (lines.next() == "The following package managers are activated:") break
                }

                lines.hasNext() shouldBe true
                lines.next() shouldBe "\tGradle"
            } finally {
                // Restore standard output.
                System.setOut(standardOut)
            }
        }

        "Activating only NPM works" {
            val inputDir = File(projectDir, "npm/package-lock")

            // Redirect standard output to a stream.
            val standardOut = System.out
            val streamOut = ByteArrayOutputStream()
            System.setOut(PrintStream(streamOut))

            try {
                val exitCode = Main.run(arrayOf(
                        "analyze",
                        "-m", "NPM",
                        "-i", inputDir.path,
                        "-o", File(outputDir, "package-lock").path
                ))

                exitCode shouldBe 0

                val lines = streamOut.toString().lineSequence().iterator()
                while (lines.hasNext() && lines.next() != "The following package managers are activated:")

                lines.hasNext() shouldBe true
                lines.next() shouldBe "\tNPM"
            } finally {
                // Restore standard output.
                System.setOut(standardOut)
            }
        }

        "Output formats are deduplicated" {
            val inputDir = File(projectDir, "gradle")

            // Redirect standard output to a stream.
            val standardOut = System.out
            val streamOut = ByteArrayOutputStream()
            System.setOut(PrintStream(streamOut))

            try {
                val exitCode = Main.run(arrayOf(
                        "analyze",
                        "-m", "Gradle",
                        "-i", inputDir.path,
                        "-o", File(outputDir, "gradle").path,
                        "-f", "json,yaml,json"
                ))

                exitCode shouldBe 0

                val lines = streamOut.toString().lines().filter { it.startsWith("Writing analyzer result to ") }

                lines.count() shouldBe 2
            } finally {
                // Restore standard output.
                System.setOut(standardOut)
            }
        }

        "Analyzer creates correct output" {
            val analyzerOutputDir = File(outputDir, "merged-results")

            val expectedResult = patchExpectedResult(
                    File(projectDir, "gradle-all-dependencies-expected-result.yml"),
                    url = vcsUrl,
                    revision = vcsRevision,
                    urlProcessed = normalizeVcsUrl(vcsUrl)
            )

            val exitCode = Main.run(arrayOf(
                    "analyze",
                    "-m", "Gradle",
                    "-i", File(projectDir, "gradle").absolutePath,
                    "-o", analyzerOutputDir.path
            ))

            exitCode shouldBe 0

            val result = File(analyzerOutputDir, "analyzer-result.yml").readText()

            patchActualResult(result, patchStartAndEndTime = true) shouldBe expectedResult
        }

        "Package curation data file is applied correctly" {
            val analyzerOutputDir = File(outputDir, "curations")

            val expectedResult = patchExpectedResult(
                    File(projectDir, "gradle-all-dependencies-expected-result-with-curations.yml"),
                    url = vcsUrl,
                    revision = vcsRevision,
                    urlProcessed = normalizeVcsUrl(vcsUrl)
            )

            // The command below should include the "--merge-results" option, but setting this option here would disable
            // the feature because JCommander just switches the value of boolean options, and the option was already set
            // to true by the test before. See: https://github.com/cbeust/jcommander/issues/378
            val exitCode = Main.run(arrayOf(
                    "analyze",
                    "-m", "Gradle",
                    "-i", File(projectDir, "gradle").absolutePath,
                    "-o", analyzerOutputDir.path,
                    "--package-curations-file", File(projectDir, "gradle/curations.yml").toString()
            ))

            exitCode shouldBe 0

            val result = File(analyzerOutputDir, "analyzer-result.yml").readText()

            patchActualResult(result, patchStartAndEndTime = true) shouldBe expectedResult
        }
    }
}
