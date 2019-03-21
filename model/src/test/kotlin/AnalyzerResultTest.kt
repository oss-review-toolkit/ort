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

package com.here.ort.model

import com.fasterxml.jackson.module.kotlin.readValue

import com.here.ort.utils.DeclaredLicenseProcessor

import io.kotlintest.matchers.haveSize
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

class AnalyzerResultTest : WordSpec() {
    private val error1 = OrtIssue(source = "source-1", message = "message-1")
    private val error2 = OrtIssue(source = "source-2", message = "message-2")
    private val error3 = OrtIssue(source = "source-3", message = "message-3")
    private val error4 = OrtIssue(source = "source-4", message = "message-4")

    private val package1 = Package.EMPTY.copy(id = Identifier("type-1", "namespace-1", "package-1", "version-1"))
    private val package2 = Package.EMPTY.copy(id = Identifier("type-2", "namespace-2", "package-2", "version-2"))
    private val package3 = Package.EMPTY.copy(id = Identifier("type-3", "namespace-3", "package-3", "version-3"))

    private val pkgRef1 = package1.toReference(errors = listOf(error1))
    private val pkgRef2 = package2.toReference(
        dependencies = sortedSetOf(package3.toReference(errors = listOf(error2)))
    )

    private val scope1 = Scope("scope-1", sortedSetOf(pkgRef1))
    private val scope2 = Scope("scope-2", sortedSetOf(pkgRef2))

    private val project1 = Project.EMPTY.copy(
        id = Identifier("type-1", "namespace-1", "project-1", "version-1"),
        scopes = sortedSetOf(scope1)
    )
    private val project2 = Project.EMPTY.copy(
        id = Identifier("type-2", "namespace-2", "project-2", "version-2"),
        scopes = sortedSetOf(scope1, scope2)
    )

    private val analyzerResult1 = ProjectAnalyzerResult(
        project1, sortedSetOf(package1.toCuratedPackage()),
        listOf(error3, error4)
    )
    private val analyzerResult2 = ProjectAnalyzerResult(
        project2,
        sortedSetOf(package1.toCuratedPackage(), package2.toCuratedPackage(), package3.toCuratedPackage()),
        listOf(error4)
    )

    init {
        "AnalyzerResult" should {
            "be serialized and deserialized correctly" {
                val mergedResults = AnalyzerResultBuilder()
                    .addResult(analyzerResult1)
                    .addResult(analyzerResult2)
                    .build()

                val serializedMergedResults = yamlMapper.writeValueAsString(mergedResults)
                val deserializedMergedResults = yamlMapper.readValue<AnalyzerResult>(serializedMergedResults)

                deserializedMergedResults shouldBe mergedResults
            }
        }

        "collectErrors" should {
            "find all errors" {
                val analyzerResult = AnalyzerResultBuilder()
                    .addResult(analyzerResult1)
                    .addResult(analyzerResult2)
                    .build()

                analyzerResult.collectErrors() shouldBe mapOf(
                    package1.id to setOf(error1),
                    package3.id to setOf(error2),
                    project1.id to setOf(error3, error4),
                    project2.id to setOf(error4)
                )
            }

            "contain declared license errors" {
                val invalidProjectLicense = sortedSetOf("invalid project license")
                val invalidPackageLicense = sortedSetOf("invalid package license")
                val analyzerResult = AnalyzerResult(
                    projects = sortedSetOf(
                        project1.copy(
                            declaredLicenses = invalidProjectLicense,
                            declaredLicensesProcessed = DeclaredLicenseProcessor.process(invalidProjectLicense),
                            scopes = sortedSetOf()
                        )
                    ),
                    packages = sortedSetOf(
                        package1.copy(
                            declaredLicenses = invalidPackageLicense,
                            declaredLicensesProcessed = DeclaredLicenseProcessor.process(invalidPackageLicense)
                        ).toCuratedPackage()
                    )
                )

                val errors = analyzerResult.collectErrors()

                errors.getValue(project1.id).let { projectErrors ->
                    projectErrors should haveSize(1)
                    projectErrors.first().severity shouldBe Severity.ERROR
                    projectErrors.first().source shouldBe project1.id.toCoordinates()
                    projectErrors.first().message shouldBe "The declared license 'invalid project license' could not " +
                            "be mapped to a valid license or parsed as an SPDX expression."
                }

                errors.getValue(package1.id).let { packageErrors ->
                    packageErrors should haveSize(1)
                    packageErrors.first().severity shouldBe Severity.ERROR
                    packageErrors.first().source shouldBe package1.id.toCoordinates()
                    packageErrors.first().message shouldBe "The declared license 'invalid package license' could not " +
                            "be mapped to a valid license or parsed as an SPDX expression."
                }
            }
        }

        "AnalyzerResultBuilder" should {
            "merge results from all files" {
                val mergedResults = AnalyzerResultBuilder()
                    .addResult(analyzerResult1)
                    .addResult(analyzerResult2)
                    .build()

                mergedResults.projects shouldBe sortedSetOf(project1, project2)
                mergedResults.packages shouldBe sortedSetOf(
                    package1.toCuratedPackage(), package2.toCuratedPackage(),
                    package3.toCuratedPackage()
                )
                mergedResults.errors shouldBe
                        sortedMapOf(project1.id to analyzerResult1.errors, project2.id to analyzerResult2.errors)
            }
        }
    }
}
