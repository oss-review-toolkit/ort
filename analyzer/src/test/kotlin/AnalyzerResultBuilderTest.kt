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

package org.ossreviewtoolkit.analyzer

import com.fasterxml.jackson.module.kotlin.readValue

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.utils.test.containExactly

class AnalyzerResultBuilderTest : WordSpec() {
    private val issue1 = OrtIssue(source = "source-1", message = "message-1")
    private val issue2 = OrtIssue(source = "source-2", message = "message-2")
    private val issue3 = OrtIssue(source = "source-3", message = "message-3")
    private val issue4 = OrtIssue(source = "source-4", message = "message-4")

    private val package1 = Package.EMPTY.copy(id = Identifier("type-1", "namespace-1", "package-1", "version-1"))
    private val package2 = Package.EMPTY.copy(id = Identifier("type-2", "namespace-2", "package-2", "version-2"))
    private val package3 = Package.EMPTY.copy(id = Identifier("type-3", "namespace-3", "package-3", "version-3"))

    private val pkgRef1 = package1.toReference(issues = listOf(issue1))
    private val pkgRef2 = package2.toReference(
        dependencies = sortedSetOf(package3.toReference(issues = listOf(issue2)))
    )

    private val scope1 = Scope("scope-1", sortedSetOf(pkgRef1))
    private val scope2 = Scope("scope-2", sortedSetOf(pkgRef2))

    private val project1 = Project.EMPTY.copy(
        id = Identifier("type-1", "namespace-1", "project-1", "version-1"),
        scopeDependencies = sortedSetOf(scope1)
    )
    private val project2 = Project.EMPTY.copy(
        id = Identifier("type-2", "namespace-2", "project-2", "version-2"),
        scopeDependencies = sortedSetOf(scope1, scope2)
    )

    private val analyzerResult1 = ProjectAnalyzerResult(
        project1, sortedSetOf(package1), listOf(issue3, issue4)
    )
    private val analyzerResult2 = ProjectAnalyzerResult(
        project2, sortedSetOf(package1, package2, package3), listOf(issue4)
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

        "collectIssues" should {
            "find all issues" {
                val analyzerResult = AnalyzerResultBuilder()
                    .addResult(analyzerResult1)
                    .addResult(analyzerResult2)
                    .build()

                analyzerResult.collectIssues() should containExactly(
                    package1.id to setOf(issue1),
                    package3.id to setOf(issue2),
                    project1.id to setOf(issue3, issue4),
                    project2.id to setOf(issue4)
                )
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
                mergedResults.issues shouldBe
                        sortedMapOf(project1.id to analyzerResult1.issues, project2.id to analyzerResult2.issues)
            }
        }
    }
}
