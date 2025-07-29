/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.reporters.ctrlx

import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.io.File

import kotlinx.serialization.json.decodeFromStream

import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.AnalyzerRun
import org.ossreviewtoolkit.model.DependencyGraph
import org.ossreviewtoolkit.model.DependencyReference
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.RootDependencyIndex
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.licenses.LicenseCategorization
import org.ossreviewtoolkit.model.licenses.LicenseCategory
import org.ossreviewtoolkit.model.licenses.LicenseClassifications
import org.ossreviewtoolkit.plugins.reporters.ctrlx.CtrlXAutomationReporter.Companion.REPORT_FILENAME
import org.ossreviewtoolkit.reporter.ORT_RESULT
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.ort.createOrtTempDir
import org.ossreviewtoolkit.utils.spdx.SpdxSingleLicenseExpression
import org.ossreviewtoolkit.utils.spdx.toSpdx
import org.ossreviewtoolkit.utils.test.getResource

class CtrlXAutomationReporterFunTest : StringSpec({
    "The official sample file can be deserialized" {
        val fossInfoResource = getResource("/sample.fossinfo.json")
        val fossInfo = fossInfoResource.openStream().use { CtrlXAutomationReporter.JSON.decodeFromStream<FossInfo>(it) }

        fossInfo.components shouldNotBeNull {
            this should haveSize(8)
        }
    }

    "Generating a report works" {
        val outputDir = tempdir()
        val reportFiles = CtrlXAutomationReporterFactory.create().generateReport(ReporterInput(ORT_RESULT), outputDir)

        reportFiles.shouldBeSingleton {
            it shouldBeSuccess outputDir / REPORT_FILENAME
        }
    }

    "Generating a report works and produces a valid fossinfo.json" {
        val reporter = CtrlXAutomationReporterFactory.create()
        val input = createReporterInput()
        val outputDir = createOrtTempDir("ctrlx-automation-reporter-test")

        val reporterResult = reporter.generateReport(input, outputDir)

        validateReport(reporterResult) {
            components shouldNotBeNull {
                this shouldHaveSize 2
                first().name shouldBe "package1"
                last().name shouldBe "package2"
            }
        }
    }

    "The reporter should only include licenses with the given category" {
        val category = "include-in-disclosure-document"
        val categorizations = listOf(
            LicenseCategorization(
                SpdxSingleLicenseExpression.parse("MIT"),
                setOf(category)
            )
        )
        val categories = listOf(LicenseCategory(category))
        val input = createReporterInput().copy(
            licenseClassifications = LicenseClassifications(
                categories = categories,
                categorizations = categorizations
            )
        )
        val reporter = CtrlXAutomationReporterFactory.create(listOf(category))
        val outputDir = createOrtTempDir("ctrlx-automation-reporter-test")

        val reporterResult = reporter.generateReport(input, outputDir)

        validateReport(reporterResult) {
            components shouldNotBeNull {
                this shouldHaveSize 1
                first().name shouldBe "package2"
            }
        }
    }
})

private fun validateReport(reporterResult: List<Result<File>>, validate: FossInfo.() -> Unit) {
    reporterResult.shouldBeSingleton { result ->
        result shouldBeSuccess { file ->
            file.name shouldBe "fossinfo.json"
            val fossInfo = file.inputStream().use {
                CtrlXAutomationReporter.JSON.decodeFromStream<FossInfo>(it)
            }

            fossInfo.validate()
        }
    }
}

private fun createReporterInput(): ReporterInput {
    val analyzedVcs = VcsInfo(
        type = VcsType.GIT,
        revision = "master",
        url = "https://github.com/path/first-project.git",
        path = "sub/path"
    )

    val package1 = Package.EMPTY.copy(
        id = Identifier("Maven:ns:package1:1.0"),
        declaredLicenses = setOf("LicenseRef-scancode-broadcom-commercial"),
        concludedLicense = "LicenseRef-scancode-broadcom-commercial".toSpdx()
    )
    val package2 = Package.EMPTY.copy(
        id = Identifier("Maven:ns:package2:1.0"),
        declaredLicenses = setOf("MIT"),
        concludedLicense = "MIT".toSpdx()
    )
    val project = Project.EMPTY.copy(
        id = Identifier.EMPTY.copy(name = "test-project"),
        scopeDependencies = setOf(
            Scope("scope-1", setOf(package1.toReference(), package2.toReference()))
        ),
        vcs = analyzedVcs,
        vcsProcessed = analyzedVcs
    )

    return ReporterInput(
        OrtResult(
            repository = Repository(
                vcs = analyzedVcs,
                vcsProcessed = analyzedVcs
            ),
            analyzer = AnalyzerRun.EMPTY.copy(
                result = AnalyzerResult(
                    projects = setOf(project),
                    packages = setOf(package1, package2),
                    dependencyGraphs = mapOf(
                        "test" to DependencyGraph(
                            listOf(package1.id, package2.id),
                            sortedSetOf(
                                DependencyGraph.DEPENDENCY_REFERENCE_COMPARATOR,
                                DependencyReference(0),
                                DependencyReference(1)
                            ),
                            mapOf(DependencyGraph.qualifyScope(project.id, "scope-1") to listOf(RootDependencyIndex(0)))
                        )
                    )
                )
            )
        )
    )
}
