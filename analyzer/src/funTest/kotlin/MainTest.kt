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
import com.here.ort.model.AnalyzerResult
import com.here.ort.model.Identifier
import com.here.ort.utils.ExpensiveTag
import com.here.ort.utils.normalizeVcsUrl
import com.here.ort.utils.safeDeleteRecursively
import com.here.ort.utils.yamlMapper

import io.kotlintest.TestCaseContext
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldNotBe
import io.kotlintest.specs.StringSpec

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream

/**
 * A test for the main entry point of the application.
 */
class MainTest : StringSpec() {
    private val projectDir = File("src/funTest/assets/projects/synthetic")
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

    private fun patchExpectedResult(filename: File) =
            filename.readText()
                    .replace("<REPLACE_URL>", vcsUrl)
                    .replace("<REPLACE_REVISION>", vcsRevision)
                    .replace("<REPLACE_URL_PROCESSED>", normalizeVcsUrl(vcsUrl))

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
            val outputAnalyzerDir = File(outputDir, "analyzer_results")

            Main.main(arrayOf(
                    "-m", "Gradle",
                    "-i", File(projectDir, "gradle").absolutePath,
                    "-o", outputAnalyzerDir.path,
                    "--merge-results"
            ))

            val resultsFile = File(outputAnalyzerDir, "all-dependencies.yml")
            val resultTree = yamlMapper.readTree(resultsFile.readText())

            val expectedResult = patchExpectedResult(File(projectDir, "gradle-all-dependencies-expected-result.yml"))
            val expectedResultTree = yamlMapper.readTree(expectedResult)

            // Compare some of the values instead of whole string to avoid problems with formatting paths.
            resultTree["repository"]["name"].asText() shouldBe
                    expectedResultTree["repository"]["name"].asText()

            yamlMapper.writeValueAsString(resultTree["projects"]) shouldBe
                    yamlMapper.writeValueAsString(expectedResultTree["projects"])

            resultTree["project_id_result_file_path_map"].asIterable().count() shouldBe
                    expectedResultTree["project_id_result_file_path_map"].asIterable().count()

            yamlMapper.writeValueAsString(resultTree["packages"]) shouldBe
                    yamlMapper.writeValueAsString(expectedResultTree["packages"])
        }.config(tags = setOf(ExpensiveTag))

        "Package curation data file is applied correctly" {
            val inputDir = File(projectDir, "gradle")
            val curationsOutputDir = File(outputDir, "curations")

            Main.main(arrayOf(
                    "-m", "Gradle",
                    "-i", inputDir.path,
                    "-o", curationsOutputDir.path,
                    "--package-curations-file", File(projectDir, "gradle/curations.yml").toString()
            ))

            val resultsFile = File(curationsOutputDir, "lib/build-gradle-dependencies.yml")
            val analyzerResult = yamlMapper.readValue(resultsFile, AnalyzerResult::class.java)
            val hamcrestCorePackage = analyzerResult.packages.find {
                it.id == Identifier("Maven", "org.hamcrest", "hamcrest-core", "1.3")
            }

            hamcrestCorePackage shouldNotBe null
            hamcrestCorePackage!!.homepageUrl shouldBe "http://hamcrest.org/JavaHamcrest/"
            hamcrestCorePackage.description shouldBe "Curated description."
            hamcrestCorePackage.declaredLicenses shouldBe sortedSetOf("curated license a", "curated license b")
        }
    }
}
