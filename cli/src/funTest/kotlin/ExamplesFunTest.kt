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

package org.ossreviewtoolkit.cli

import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.GreenMailUtil
import com.icegreen.greenmail.util.ServerSetup

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.shouldContain

import java.io.File
import java.io.IOException
import java.time.Instant

import org.ossreviewtoolkit.evaluator.Evaluator
import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.AnalyzerRun
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.CopyrightGarbage
import org.ossreviewtoolkit.model.config.NotifierConfiguration
import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.Resolutions
import org.ossreviewtoolkit.model.config.SendMailConfiguration
import org.ossreviewtoolkit.model.licenses.LicenseClassifications
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.notifier.Notifier
import org.ossreviewtoolkit.plugins.api.PluginConfig
import org.ossreviewtoolkit.reporter.HowToFixTextProvider
import org.ossreviewtoolkit.reporter.ReporterFactory
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.ort.ORT_PACKAGE_CONFIGURATION_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_PACKAGE_CURATIONS_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_REPO_CONFIG_FILENAME
import org.ossreviewtoolkit.utils.ort.ORT_RESOLUTIONS_FILENAME
import org.ossreviewtoolkit.utils.spdx.toSpdx
import org.ossreviewtoolkit.utils.test.readResourceValue

class ExamplesFunTest : StringSpec({
    val examplesDir = File("../examples")
    val exampleFiles = examplesDir.walk().maxDepth(1).filterTo(mutableListOf()) {
        it.isFile && it.extension != "md"
    }

    fun takeExampleFile(name: String) = exampleFiles.single { it.name == name }.also { exampleFiles.remove(it) }

    "Listing example files succeeds" {
        exampleFiles shouldNot beEmpty()
    }

    "The ORT repository configuration files are parsable" {
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

    "The Copyright garbage file can be deserialized" {
        shouldNotThrow<IOException> {
            takeExampleFile("copyright-garbage.yml").readValue<CopyrightGarbage>()
        }
    }

    "The package curations file can be deserialized" {
        shouldNotThrow<IOException> {
            takeExampleFile(ORT_PACKAGE_CURATIONS_FILENAME).readValue<List<PackageCuration>>()
        }
    }

    "The package configuration file can be deserialized" {
        shouldNotThrow<IOException> {
            takeExampleFile(ORT_PACKAGE_CONFIGURATION_FILENAME).readValue<PackageConfiguration>()
        }
    }

    "The license classifications file can be deserialized" {
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

    "The resolutions file can be deserialized" {
        shouldNotThrow<IOException> {
            takeExampleFile(ORT_RESOLUTIONS_FILENAME).readValue<Resolutions>()
        }
    }

    "The Asciidoctor PDF theme file is a valid" {
        val reporter = ReporterFactory.ALL.getValue("PdfTemplate")
        val outputDir = tempdir()

        takeExampleFile("asciidoctor-pdf-theme.yml")

        val report = reporter.create(
            PluginConfig(options = mapOf("pdf.theme.file" to examplesDir.resolve("asciidoctor-pdf-theme.yml").path))
        ).generateReport(ReporterInput(OrtResult.EMPTY), outputDir)

        report shouldHaveSize 1
    }

    "The rules script can be run" {
        val ortResult = readResourceValue<OrtResult>("/semver4j-ort-result.yml")
        val licenseFile = File("../examples/license-classifications.yml")
        val evaluator = Evaluator(
            ortResult = ortResult,
            licenseClassifications = licenseFile.readValue()
        )

        val script = takeExampleFile("example.rules.kts").readText()

        val result = evaluator.run(script)

        result.violations.map { it.rule } should containExactlyInAnyOrder(
            "COPYLEFT_LIMITED_IN_SOURCE",
            "DEPRECATED_SCOPE_EXCLUDE_REASON_IN_ORT_YML",
            "HIGH_SEVERITY_VULNERABILITY_IN_PACKAGE",
            "MISSING_CONTRIBUTING_FILE",
            "MISSING_README_FILE_LICENSE_SECTION",
            "UNHANDLED_LICENSE",
            "VULNERABILITY_IN_PACKAGE"
        )
    }

    "The notifications script can be run" {
        val greenMail = GreenMail(ServerSetup.SMTP.dynamicPort())
        greenMail.setUser("no-reply@oss-review-toolkit.org", "no-reply@oss-review-toolkit.org", "pwd")
        greenMail.start()

        val ortResult = createOrtResultWithIssue()
        val notifier = Notifier(
            ortResult,
            NotifierConfiguration(
                SendMailConfiguration(
                    hostName = "localhost",
                    port = greenMail.smtp.serverSetup.port,
                    username = "no-reply@oss-review-toolkit.org",
                    password = "pwd",
                    useSsl = false,
                    fromAddress = "no-reply@oss-review-toolkit.org"
                )
            )
        )

        val script = takeExampleFile("example.notifications.kts").readText()

        notifier.run(script)

        greenMail.waitForIncomingEmail(1000, 1) shouldBe true
        val actualBody = GreenMailUtil.getBody(greenMail.receivedMessages.first())

        actualBody shouldContain "Content-Type: text/html; charset=UTF-8"
        actualBody shouldContain "Content-Type: text/plain; charset=UTF-8" // Fallback
        actualBody shouldContain "Number of issues found: ${ortResult.getIssues().size}"

        greenMail.stop()
    }

    "The how-to-fix-text script provides the expected texts" {
        val script = takeExampleFile("example.how-to-fix-text-provider.kts").readText()
        val howToFixTextProvider = HowToFixTextProvider.fromKotlinScript(script, OrtResult.EMPTY)
        val issue = Issue(
            message = "ERROR: Timeout after 360 seconds while scanning file 'src/res/data.json'.",
            source = "ScanCode",
            severity = Severity.ERROR,
            timestamp = Instant.now()
        )

        val howToFixText = howToFixTextProvider.getHowToFixText(issue)

        howToFixText shouldContain "Manually verify that the file does not contain any license information."
    }

    "All example files are tested" {
        exampleFiles should beEmpty()
    }
})

private fun createOrtResultWithIssue() =
    OrtResult.EMPTY.copy(
        analyzer = AnalyzerRun.EMPTY.copy(
            result = AnalyzerResult.EMPTY.copy(
                issues = mapOf(
                    Identifier("Maven:org.oss-review-toolkit:example:1.0") to listOf(
                        Issue(source = "", message = "issue")
                    )
                )
            )
        )
    )
