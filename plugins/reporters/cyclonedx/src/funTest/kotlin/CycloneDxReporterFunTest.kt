/*
 * Copyright (C) 2019 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.reporters.cyclonedx

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.TestConfiguration
import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.inspectors.forAll
import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.cyclonedx.Format
import org.cyclonedx.Version
import org.cyclonedx.model.Component
import org.cyclonedx.parsers.XmlParser

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.plugins.licensefactproviders.api.CompositeLicenseFactProvider
import org.ossreviewtoolkit.plugins.licensefactproviders.api.LicenseFactProvider
import org.ossreviewtoolkit.plugins.licensefactproviders.spdx.SpdxLicenseFactProviderFactory
import org.ossreviewtoolkit.plugins.reporters.cyclonedx.CycloneDxReporter.Companion.REPORT_BASE_FILENAME
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.common.normalizeLineBreaks
import org.ossreviewtoolkit.utils.test.matchJsonSchema
import org.ossreviewtoolkit.utils.test.readResource

class CycloneDxReporterFunTest : WordSpec({
    val schemaJson by lazy { readResource("/bom-$DEFAULT_SCHEMA_VERSION_NAME.schema.json") }

    "Requesting a single BOM for all projects" should {
        "create the expected XML file according to schema version $DEFAULT_SCHEMA_VERSION_NAME" {
            val expectedBom = readResource("/cyclonedx-reporter-expected-result.xml")

            val bom = generateSingleBomReport(
                ortResult = ORT_RESULT_WITH_VULNERABILITIES,
                format = Format.XML,
                licenseFactProvider = SpdxLicenseFactProviderFactory.create()
            )

            bom should matchCycloneDxXmlSchema(DEFAULT_SCHEMA_VERSION_NAME)
            bom.patchCycloneDxResult() shouldBe expectedBom
        }

        "create the expected XML file even if some copyrights contain non-printable characters" {
            val bom = generateSingleBomReport(
                ortResult = ORT_RESULT_WITH_ILLEGAL_COPYRIGHTS,
                format = Format.XML
            )

            bom should matchCycloneDxXmlSchema(DEFAULT_SCHEMA_VERSION_NAME)
        }

        "create the expected JSON file according to schema version $DEFAULT_SCHEMA_VERSION_NAME" {
            val expectedBom = readResource("/cyclonedx-reporter-expected-result.json")

            val bom = generateSingleBomReport(
                ortResult = ORT_RESULT_WITH_VULNERABILITIES,
                format = Format.JSON,
                licenseFactProvider = SpdxLicenseFactProviderFactory.create()
            )

            bom should matchJsonSchema(schemaJson)
            bom.patchCycloneDxResult() shouldEqualJson expectedBom
        }

        "create the expected JSON file if there are projects with different namespace and version" {
            val expectedBom = readResource("/cyclonedx-reporter-expected-result-different-projects.json")
            val otherProject = Project.EMPTY.copy(
                id = Identifier("Maven:@other:foo:2.0.0"),
                definitionFilePath = "other/foo-2.0.0/pom.xml",
                vcs = VcsInfo(
                    type = VcsType.GIT,
                    url = "https://github.com/oss-review-toolkit/ort.git",
                    revision = "main"
                ),
                vcsProcessed = VcsInfo(
                    type = VcsType.GIT,
                    url = "https://github.com/oss-review-toolkit/ort.git",
                    revision = "main"
                )
            )

            val bom = generateSingleBomReport(
                ortResult = createResultWithAdditionalProject(otherProject),
                format = Format.JSON,
                licenseFactProvider = SpdxLicenseFactProviderFactory.create()
            )

            bom.patchCycloneDxResult() shouldEqualJson expectedBom
        }

        "create the expected JSON file if there is a single top-level project" {
            val expectedBom = readResource("/cyclonedx-reporter-expected-result-top-level-project.json")
            val otherProject = Project.EMPTY.copy(
                id = Identifier("NPM:@ort:root-test-project:1.0"),
                definitionFilePath = "package.json",
                vcs = VcsInfo(
                    type = VcsType.GIT,
                    url = "https://github.com/oss-review-toolkit/ort.git",
                    revision = "main"
                ),
                vcsProcessed = VcsInfo(
                    type = VcsType.GIT,
                    url = "https://github.com/oss-review-toolkit/ort.git",
                    revision = "main"
                )
            )

            val bom = generateSingleBomReport(
                ortResult = createResultWithAdditionalProject(otherProject),
                format = Format.JSON,
                licenseFactProvider = SpdxLicenseFactProviderFactory.create()
            )

            bom.patchCycloneDxResult() shouldEqualJson expectedBom
        }

        "support configuring the type of the main component" {
            val expectedBom = readResource("/cyclonedx-reporter-expected-result-type-override.json")

            val bom = generateSingleBomReport(
                ortResult = ORT_RESULT_WITH_VULNERABILITIES,
                format = Format.JSON,
                singleBomComponentType = Component.Type.LIBRARY,
                licenseFactProvider = SpdxLicenseFactProviderFactory.create()
            )

            bom.patchCycloneDxResult() shouldEqualJson expectedBom
        }

        "support configuring the name of the main component" {
            val expectedBom = readResource("/cyclonedx-reporter-expected-result-name-override.json")
            val topLevelProject = Project.EMPTY.copy(
                id = Identifier("NPM:@ort:root-test-project:1.0"),
                definitionFilePath = "package.json",
                vcs = VcsInfo(
                    type = VcsType.GIT,
                    url = "https://github.com/oss-review-toolkit/ort.git",
                    revision = "main"
                ),
                vcsProcessed = VcsInfo(
                    type = VcsType.GIT,
                    url = "https://github.com/oss-review-toolkit/ort.git",
                    revision = "main"
                )
            )

            val bom = generateSingleBomReport(
                ortResult = createResultWithAdditionalProject(topLevelProject),
                format = Format.JSON,
                singleBomComponentName = "eric",
                licenseFactProvider = SpdxLicenseFactProviderFactory.create()
            )

            bom.patchCycloneDxResult() shouldEqualJson expectedBom
        }
    }

    "Requesting separate BOMs per project" should {
        "create valid XML files according to schema version $DEFAULT_SCHEMA_VERSION_NAME" {
            val boms = generateMultiBomReport(
                ortResult = ORT_RESULT_WITH_VULNERABILITIES,
                format = Format.XML
            )

            boms shouldHaveSize 2
            boms.forAll { bom ->
                bom should matchCycloneDxXmlSchema(DEFAULT_SCHEMA_VERSION_NAME)
            }
        }

        "create expected JSON files according to schema version $DEFAULT_SCHEMA_VERSION_NAME" {
            val (bomWithFindings, bomWithoutFindings) = generateMultiBomReport(
                ortResult = ORT_RESULT,
                format = Format.JSON,
                licenseFactProvider = SpdxLicenseFactProviderFactory.create()
            ).also {
                it shouldHaveSize 2
                it.forAll { bom ->
                    bom should matchJsonSchema(schemaJson)
                }
            }

            bomWithFindings.patchCycloneDxResult() shouldBe readResource(
                "/cyclonedx-reporter-expected-result-with-findings.json"
            )

            bomWithoutFindings.patchCycloneDxResult() shouldBe readResource(
                "/cyclonedx-reporter-expected-result-without-findings.json"
            )
        }
    }
})

private fun String.patchCycloneDxResult(): String =
    replaceFirst(
        """urn:uuid:[a-f0-9]{8}(?:-[a-f0-9]{4}){4}[a-f0-9]{8}""".toRegex(),
        "urn:uuid:12345678-1234-1234-1234-123456789012"
    ).replaceFirst(
        """(timestamp[>"](\s*:\s*")?)\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z""".toRegex(),
        "$11970-01-01T00:00:00Z"
    ).replaceFirst(
        """(version[>"](\s*:\s*")?)[\w.+-]+""".toRegex(),
        "$1deadbeef"
    )

/**
 * Create an [OrtResult] based on the standard test data that contains the additional [project].
 */
private fun createResultWithAdditionalProject(project: Project): OrtResult =
    ORT_RESULT_WITH_VULNERABILITIES.copy(
        analyzer = ORT_RESULT_WITH_VULNERABILITIES.analyzer?.copy(
            result = requireNotNull(ORT_RESULT_WITH_VULNERABILITIES.analyzer).result.copy(
                projects = ORT_RESULT_WITH_VULNERABILITIES.analyzer?.result?.projects.orEmpty() + project
            )
        )
    )

private fun matchCycloneDxXmlSchema(schemaVersion: String): Matcher<String> =
    Matcher { actual ->
        val version = Version.entries.single { it.versionString == schemaVersion }
        val parseExceptions = XmlParser().validate(actual.byteInputStream(), version)

        MatcherResult(
            parseExceptions.isEmpty(),
            { parseExceptions.joinToString(separator = "\n") { it.message.orEmpty() } },
            { "Expected some parse exception against XML schema, but everything matched" }
        )
    }

private fun TestConfiguration.generateSingleBomReport(
    ortResult: OrtResult,
    format: Format,
    singleBomComponentName: String = "",
    singleBomComponentType: Component.Type = Component.Type.APPLICATION,
    licenseFactProvider: LicenseFactProvider = CompositeLicenseFactProvider(emptyList())
): String {
    val reporter = CycloneDxReporterFactory.create(
        schemaVersion = SchemaVersion.entries.single { it.version.versionString == DEFAULT_SCHEMA_VERSION_NAME },
        singleBomComponentName = singleBomComponentName,
        singleBomComponentType = singleBomComponentType,
        outputFileFormats = listOf(format)
    )

    return reporter.generateReport(
        input = ReporterInput(ortResult, licenseFactProvider = licenseFactProvider),
        outputDir = tempdir()
    ).single().shouldBeSuccess().apply {
        name shouldBe "$REPORT_BASE_FILENAME.${format.extension}"
    }.readText().normalizeLineBreaks()
}

private fun TestConfiguration.generateMultiBomReport(
    ortResult: OrtResult,
    format: Format,
    licenseFactProvider: LicenseFactProvider = CompositeLicenseFactProvider(emptyList())
): List<String> {
    val reporter = CycloneDxReporterFactory.create(
        schemaVersion = SchemaVersion.entries.single { it.version.versionString == DEFAULT_SCHEMA_VERSION_NAME },
        singleBom = false,
        outputFileFormats = listOf(format)
    )

    return reporter.generateReport(
        input = ReporterInput(ortResult, licenseFactProvider = licenseFactProvider),
        outputDir = tempdir()
    ).map { it.shouldBeSuccess().readText().normalizeLineBreaks() }
}
