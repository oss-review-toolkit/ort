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

package org.ossreviewtoolkit.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.maps.shouldContainExactly

class ProjectAnalyzerResultTest : StringSpec({
    "collectIssues should find all issues" {
        val issue1 = OrtIssue(source = "source-1", message = "issue-1")
        val issue2 = OrtIssue(source = "source-2", message = "issue-2")
        val issue3 = OrtIssue(source = "source-3", message = "issue-3")
        val issue4 = OrtIssue(source = "source-4", message = "issue-4")
        val issue5 = OrtIssue(source = "source-5", message = "issue-5")
        val issue6 = OrtIssue(source = "source-6", message = "issue-6")
        val issue7 = OrtIssue(source = "source-7", message = "issue-7")
        val issue8 = OrtIssue(source = "source-8", message = "issue-8")

        val result = ProjectAnalyzerResult(
            project = Project(
                id = Identifier("type", "namespace", "name", "version"),
                definitionFilePath = "definitionFilePath",
                declaredLicenses = sortedSetOf(),
                vcs = VcsInfo.EMPTY,
                homepageUrl = "",
                scopes = sortedSetOf(
                    Scope(
                        name = "scope 1",
                        dependencies = sortedSetOf(
                            PackageReference(
                                Identifier(
                                    type = "type1",
                                    namespace = "namespace1",
                                    name = "name1",
                                    version = "version1"
                                ),
                                dependencies = sortedSetOf(
                                    PackageReference(
                                        Identifier(
                                            type = "type2",
                                            namespace = "namespace2",
                                            name = "name2",
                                            version = "version2"
                                        ),
                                        dependencies = sortedSetOf(),
                                        issues = listOf(issue1, issue2)
                                    )
                                ),
                                issues = listOf(issue3, issue4)
                            ),
                            PackageReference(
                                Identifier(
                                    type = "type3",
                                    namespace = "namespace3",
                                    name = "name3",
                                    version = "version3"
                                ),
                                dependencies = sortedSetOf(),
                                issues = listOf(issue5, issue6)
                            )
                        )
                    )
                )
            ),
            packages = sortedSetOf(),
            issues = listOf(issue7, issue8)
        )

        result.collectIssues() shouldContainExactly
                mapOf(
                    Identifier("type:namespace:name:version") to listOf(issue7, issue8),
                    Identifier("type1:namespace1:name1:version1") to listOf(issue3, issue4),
                    Identifier("type2:namespace2:name2:version2") to listOf(issue1, issue2),
                    Identifier("type3:namespace3:name3:version3") to listOf(issue5, issue6)
                )
    }
})
