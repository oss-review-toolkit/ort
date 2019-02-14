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

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

class ProjectAnalyzerResultTest : StringSpec({
    "collectErrors should find all errors" {
        val error1 = OrtIssue(source = "source-1", message = "error-1")
        val error2 = OrtIssue(source = "source-2", message = "error-2")
        val error3 = OrtIssue(source = "source-3", message = "error-3")
        val error4 = OrtIssue(source = "source-4", message = "error-4")
        val error5 = OrtIssue(source = "source-5", message = "error-5")
        val error6 = OrtIssue(source = "source-6", message = "error-6")
        val error7 = OrtIssue(source = "source-7", message = "error-7")
        val error8 = OrtIssue(source = "source-8", message = "error-8")

        val result = ProjectAnalyzerResult(
                project = Project(
                        id = Identifier("type", "namespace", "name", "version"),
                        definitionFilePath = "definitionFilePath",
                        declaredLicenses = sortedSetOf(),
                        declaredLicensesProcessed = null,
                        vcs = VcsInfo.EMPTY,
                        vcsProcessed = VcsInfo.EMPTY,
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
                                                                        errors = listOf(error1, error2)
                                                                )
                                                        ),
                                                        errors = listOf(error3, error4)
                                                ),
                                                PackageReference(
                                                        Identifier(
                                                                type = "type3",
                                                                namespace = "namespace3",
                                                                name = "name3",
                                                                version = "version3"
                                                        ),
                                                        dependencies = sortedSetOf(),
                                                        errors = listOf(error5, error6)
                                                )
                                        )
                                )
                        )
                ),
                packages = sortedSetOf(),
                errors = listOf(error7, error8)
        )

        val errors = result.collectErrors()
        errors.size shouldBe 4
        errors[Identifier("type:namespace:name:version")] shouldBe listOf(error7, error8)
        errors[Identifier("type1:namespace1:name1:version1")] shouldBe listOf(error3, error4)
        errors[Identifier("type2:namespace2:name2:version2")] shouldBe listOf(error1, error2)
        errors[Identifier("type3:namespace3:name3:version3")] shouldBe listOf(error5, error6)
    }
})
