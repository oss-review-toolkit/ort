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

package com.here.ort.model

import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec

class AnalyzerResultTest : StringSpec({
    "collectErrors" should {
        "find all errors" {
            val result = AnalyzerResult(
                    allowDynamicVersions = true,
                    project = Project(
                            id = Identifier("provider", "namespace", "name", "version"),
                            definitionFilePath = "definitionFilePath",
                            declaredLicenses = sortedSetOf(),
                            aliases = listOf(),
                            vcs = VcsInfo.EMPTY,
                            vcsProcessed = VcsInfo.EMPTY,
                            homepageUrl = "",
                            scopes = sortedSetOf(
                                    Scope(
                                            name = "scope 1",
                                            delivered = true,
                                            dependencies = sortedSetOf(
                                                    PackageReference(
                                                            Identifier(
                                                                    provider = "provider1",
                                                                    namespace = "namespace1",
                                                                    name = "name1",
                                                                    version = "version1"
                                                            ),
                                                            dependencies = sortedSetOf(
                                                                    PackageReference(
                                                                            Identifier(
                                                                                    provider = "provider2",
                                                                                    namespace = "namespace2",
                                                                                    name = "name2",
                                                                                    version = "version2"
                                                                            ),
                                                                            dependencies = sortedSetOf(),
                                                                            errors = listOf("2.1", "2.2")
                                                                    )
                                                            ),
                                                            errors = listOf("1.1", "1.2")
                                                    ),
                                                    PackageReference(
                                                            Identifier(
                                                                    provider = "provider3",
                                                                    namespace = "namespace3",
                                                                    name = "name3",
                                                                    version = "version3"
                                                            ),
                                                            dependencies = sortedSetOf(),
                                                            errors = listOf("3.1", "3.2")
                                                    )
                                            )
                                    )
                            )
                    ),
                    packages = sortedSetOf(),
                    errors = listOf("a", "b")
            )

            val errors = result.collectErrors()
            errors.size shouldBe 4
            errors[Identifier.fromString("provider:namespace:name:version")] shouldBe listOf("a", "b")
            errors[Identifier.fromString("provider1:namespace1:name1:version1")] shouldBe listOf("1.1", "1.2")
            errors[Identifier.fromString("provider2:namespace2:name2:version2")] shouldBe listOf("2.1", "2.2")
            errors[Identifier.fromString("provider3:namespace3:name3:version3")] shouldBe listOf("3.1", "3.2")
        }
    }
})
