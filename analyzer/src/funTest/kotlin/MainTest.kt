/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

package com.here.ort.analyzer

import com.here.ort.downloader.VersionControlSystem
import com.here.ort.utils.normalizeVcsUrl
import com.here.ort.utils.safeDeleteRecursively
import com.here.ort.utils.searchUpwardsForSubdirectory

import io.kotlintest.TestCaseContext
import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

/**
 * A test for the main entry point of the application.
 */
class MainTest : StringSpec() {
    private val rootDir = File(".").searchUpwardsForSubdirectory(".git")!!
    private val projectDir = File(rootDir, "analyzer/src/funTest/assets/projects/synthetic")
    private val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()

    private lateinit var outputDir: File

    // Required to make lateinit of outputDir work.
    override val oneInstancePerTest = false

    override fun interceptTestCase(context: TestCaseContext, test: () -> Unit) {
        outputDir = createTempDir()
        try {
            super.interceptTestCase(context, test)
        } finally {
            outputDir.safeDeleteRecursively()
        }
    }

    private fun patchExpectedResult(filename: File, outputDirName: String): String {
        val rootPath = rootDir.invariantSeparatorsPath
        val outputPath = "${outputDir.invariantSeparatorsPath}/$outputDirName"

        return filename.readText()
                .replace("<REPLACE_URL>", vcsUrl)
                .replace("<REPLACE_REVISION>", vcsRevision)
                .replace("<REPLACE_URL_PROCESSED>", normalizeVcsUrl(vcsUrl))
                .replace("<REPLACE_REPOSITORY_PATH>", rootPath)
                .replace("<REPLACE_PROJECT_PATH>", outputPath)
    }

    init {
        "Activating only Gradle works" {
            val inputDir = File(projectDir, "gradle")

            // Redirect standard output to a stream.
            val standardOut = System.out
            val streamOut = ByteArrayOutputStream()
            System.setOut(PrintStream(streamOut))

            Main.main(arrayOf(
                    "-m", "Gradle",
                    "-i", inputDir.path,
                    "-o", File(outputDir, "gradle").path
            ))

            // Restore standard output.
            System.setOut(standardOut)
            val lines = streamOut.toString().lineSequence().iterator()

            lines.next() shouldBe "The following package managers are activated:"
            lines.next() shouldBe "\tGradle"
            lines.next() shouldBe "Scanning project path:"
            lines.next() shouldBe "\t" + inputDir.absolutePath
            lines.next() shouldBe "Gradle projects found in:"
        }

        "Activating only NPM works" {
            val inputDir = File(projectDir, "npm/package-lock")

            // Redirect standard output to a stream.
            val standardOut = System.out
            val streamOut = ByteArrayOutputStream()
            System.setOut(PrintStream(streamOut))

            Main.main(arrayOf(
                    "-m", "NPM",
                    "-i", inputDir.path,
                    "-o", File(outputDir, "package-lock").path
            ))

            // Restore standard output.
            System.setOut(standardOut)
            val lines = streamOut.toString().lineSequence().iterator()

            lines.next() shouldBe "The following package managers are activated:"
            lines.next() shouldBe "\tNPM"
            lines.next() shouldBe "Scanning project path:"
            lines.next() shouldBe "\t" + inputDir.absolutePath
            lines.next() shouldBe "NPM projects found in:"
        }

        "Merging into single results file creates correct output" {
            val analyzerOutputDir = File(outputDir, "merged-results")

            val expectedResult = patchExpectedResult(File(projectDir, "gradle-all-dependencies-expected-result.yml"),
                    "merged-results")

            Main.main(arrayOf(
                    "-m", "Gradle",
                    "-i", File(projectDir, "gradle").absolutePath,
                    "-o", analyzerOutputDir.path,
                    "--merge-results"
            ))

            val result = File(analyzerOutputDir, "all-dependencies.yml").readText()

            result shouldBe expectedResult
        }

        "Package curation data file is applied correctly" {
            val analyzerOutputDir = File(outputDir, "curations")

            val expectedResult = patchExpectedResult(
                    File(projectDir, "gradle-all-dependencies-expected-result-with-curations.yml"), "curations")

            // The command below should include the "--merge-results" option, but setting this option here would disable
            // the feature because JCommander just switches the value of boolean options, and the option was already set
            // to true by the test before. See: https://github.com/cbeust/jcommander/issues/378
            Main.main(arrayOf(
                    "-m", "Gradle",
                    "-i", File(projectDir, "gradle").absolutePath,
                    "-o", analyzerOutputDir.path,
                    "--package-curations-file", File(projectDir, "gradle/curations.yml").toString()
            ))

            val result = File(analyzerOutputDir, "all-dependencies.yml").readText()

            result shouldBe expectedResult
        }
    }
}
