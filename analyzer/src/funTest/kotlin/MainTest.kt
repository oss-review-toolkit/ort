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

import com.here.ort.model.AnalyzerResult
import com.here.ort.model.Identifier
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
    private val syntheticProjectDir = File("src/funTest/assets/projects/synthetic")

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

    init {
        "Activating only Gradle works" {
            val inputDir = File(syntheticProjectDir, "gradle")

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
            val inputDir = File(syntheticProjectDir, "npm/package-lock")

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

        "Package curation data file is applied correctly" {
            val inputDir = File(syntheticProjectDir, "gradle")
            val curationsOutputDir = File(outputDir, "curations")

            Main.main(arrayOf(
                    "-m", "Gradle",
                    "-i", inputDir.path,
                    "-o", curationsOutputDir.path,
                    "--package-curations-file", "src/funTest/assets/projects/synthetic/gradle/curations.yml"
            ))

            val resultsFile = File(curationsOutputDir, "lib/build-gradle-dependencies.yml")
            val analyzerResult = yamlMapper.readValue(resultsFile, AnalyzerResult::class.java)
            val hamcrestCorePackage = analyzerResult.packages.find {
                it.id == Identifier("Gradle", "org.hamcrest", "hamcrest-core", "1.3")
            }

            hamcrestCorePackage shouldNotBe null
            hamcrestCorePackage!!.homepageUrl shouldBe "http://hamcrest.org/JavaHamcrest/"
            hamcrestCorePackage.description shouldBe "Curated description."
            hamcrestCorePackage.declaredLicenses shouldBe sortedSetOf("curated license a", "curated license b")
        }
    }
}
