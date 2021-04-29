/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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

package org.ossreviewtoolkit

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.shouldContain

import java.io.File
import java.io.IOException
import java.time.Instant

import org.ossreviewtoolkit.evaluator.Evaluator
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.CopyrightGarbage
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.Resolutions
import org.ossreviewtoolkit.model.licenses.LicenseClassifications
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.utils.createLicenseInfoResolver
import org.ossreviewtoolkit.reporter.HowToFixTextProvider
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.reporter.reporters.AsciiDocTemplateReporter
import org.ossreviewtoolkit.spdx.toSpdx
import org.ossreviewtoolkit.utils.ORT_REPO_CONFIG_FILENAME
import org.ossreviewtoolkit.utils.test.createSpecTempDir
import org.ossreviewtoolkit.utils.test.shouldNotBeNull

class ExamplesFunTest : StringSpec() {
    private val examplesDir = File("../examples")
    private val exampleFiles = examplesDir.walk().filterTo(mutableListOf()) { it.isFile && it.extension != "md" }

    private fun takeExampleFile(name: String) = exampleFiles.single { it.name == name }.also { exampleFiles.remove(it) }

    init {
        "Listing examples files succeeded" {
            exampleFiles shouldNot beEmpty()
        }

        "ort.yml examples are parsable" {
            val excludesExamples = exampleFiles.filter { it.name.endsWith(ORT_REPO_CONFIG_FILENAME) }
            exampleFiles.removeAll(excludesExamples)

            excludesExamples.forEach { file ->
                withClue(file.name) {
                    shouldNotThrow<IOException> {
                        file.readValue<RepositoryConfiguration>()
                    }
                }
            }
        }

        "copyright-garbage.yml can be deserialized" {
            shouldNotThrow<IOException> {
                takeExampleFile("copyright-garbage.yml").readValue<CopyrightGarbage>()
            }
        }

        "curations.yml can be deserialized" {
            shouldNotThrow<IOException> {
                takeExampleFile("curations.yml").readValue<List<PackageCuration>>()
            }
        }

        "license-classifications.yml can be deserialized" {
            shouldNotThrow<IOException> {
                val classifications =
                    takeExampleFile("license-classifications.yml").readValue<LicenseClassifications>()

                classifications.categories.filter { it.description.isNotEmpty() } shouldNot beEmpty()
                classifications.categoryNames shouldContain "public-domain"
                val classificationsForMit = classifications["MIT".toSpdx()]
                classificationsForMit shouldNotBeNull {
                    shouldContain("permissive")
                }
            }
        }

        "resolutions.yml can be deserialized" {
            shouldNotThrow<IOException> {
                takeExampleFile("resolutions.yml").readValue<Resolutions>()
            }
        }

        "how-to-fix-text-provider.kts provides the expected how-to-fix text" {
            val script = takeExampleFile("how-to-fix-text-provider.kts").readText()
            val howToFixTextProvider = HowToFixTextProvider.fromKotlinScript(script, OrtResult.EMPTY)
            val issue = OrtIssue(
                message = "ERROR: Timeout after 360 seconds while scanning file 'src/res/data.json'.",
                source = "ScanCode",
                severity = Severity.ERROR,
                timestamp = Instant.now()
            )

            val howToFixText = howToFixTextProvider.getHowToFixText(issue)

            howToFixText shouldContain "Manually verify that the file does not contain any license information."
        }

        "rules.kts can be compiled and executed" {
            val resultFile = File("src/funTest/assets/semver4j-analyzer-result.yml")
            val licenseFile = File("../examples/license-classifications.yml")
            val ortResult = resultFile.readValue<OrtResult>()
            val evaluator = Evaluator(
                ortResult = ortResult,
                licenseInfoResolver = ortResult.createLicenseInfoResolver(),
                licenseClassifications = licenseFile.readValue()
            )

            val script = takeExampleFile("rules.kts").readText()

            val result = evaluator.run(script)

            result.violations shouldHaveSize 3
            val failedRules = result.violations.map { it.rule }
            failedRules shouldContainExactlyInAnyOrder listOf(
                "UNHANDLED_LICENSE",
                "COPYLEFT_LIMITED_IN_SOURCE",
                "HIGH_SEVERITY_VULNERABILITY_IN_PACKAGE"
            )
        }

        "asciidoctor-pdf-theme.yml is a valid asciidoctor-pdf theme" {
            val outputDir = createSpecTempDir()

            takeExampleFile("asciidoctor-pdf-theme.yml")

            val report = AsciiDocTemplateReporter().generateReport(
                ReporterInput(OrtResult.EMPTY),
                outputDir,
                mapOf("pdf.theme.file" to examplesDir.resolve("asciidoctor-pdf-theme.yml").path)
            )

            report shouldHaveSize 1
        }

        "All example files should have been tested" {
            exampleFiles should beEmpty()
        }
    }
}
