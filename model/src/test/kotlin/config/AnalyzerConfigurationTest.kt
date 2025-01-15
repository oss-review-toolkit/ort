/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.model.config

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.maps.containExactly as containExactlyEntries
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot

class AnalyzerConfigurationTest : WordSpec({
    "getPackageManagerConfiguration()" should {
        "be case-insensitive" {
            val config = AnalyzerConfiguration(
                packageManagers = mapOf(
                    "Gradle" to PackageManagerConfiguration()
                )
            )

            config.getPackageManagerConfiguration("Gradle") shouldNot beNull()
            config.getPackageManagerConfiguration("gradle") shouldNot beNull()
            config.getPackageManagerConfiguration("gRADLE") shouldNot beNull()
        }
    }

    "merge()" should {
        "overwrite properties with values from other" {
            val self = AnalyzerConfiguration(
                allowDynamicVersions = false,
                enabledPackageManagers = listOf("Gradle"),
                disabledPackageManagers = listOf("NPM")
            )

            val other = RepositoryAnalyzerConfiguration(
                allowDynamicVersions = true,
                enabledPackageManagers = listOf("Maven"),
                disabledPackageManagers = listOf("SBT"),
                skipExcluded = true
            )

            with(self.merge(other)) {
                allowDynamicVersions shouldBe true
                enabledPackageManagers should containExactly("Maven")
                disabledPackageManagers should containExactly("SBT")
                skipExcluded shouldBe true
            }
        }

        "keep values which are null in other" {
            val self = AnalyzerConfiguration(
                allowDynamicVersions = true,
                enabledPackageManagers = listOf("Gradle"),
                disabledPackageManagers = listOf("NPM"),
                skipExcluded = true
            )

            val other = RepositoryAnalyzerConfiguration(
                allowDynamicVersions = null,
                enabledPackageManagers = null,
                disabledPackageManagers = null,
                skipExcluded = null
            )

            self.merge(other) shouldBe self
        }

        "merge the package manager configurations" {
            val self = AnalyzerConfiguration(
                packageManagers = mapOf(
                    "Gradle" to PackageManagerConfiguration(
                        mustRunAfter = null,
                        options = mapOf("option1" to "value1")
                    )
                )
            )

            val other = RepositoryAnalyzerConfiguration(
                packageManagers = mapOf(
                    "gradle" to PackageManagerConfiguration(
                        mustRunAfter = listOf("NPM"),
                        options = mapOf("option2" to "value2")
                    ),
                    "NPM" to PackageManagerConfiguration()
                )
            )

            with(self.merge(other)) {
                packageManagers shouldNotBeNull {
                    this should containExactlyEntries(
                        "Gradle" to PackageManagerConfiguration(
                            mustRunAfter = listOf("NPM"),
                            options = mapOf("option1" to "value1", "option2" to "value2")
                        ),
                        "NPM" to PackageManagerConfiguration()
                    )
                }
            }
        }
    }

    "withPackageManagerOption()" should {
        "add a non-existing option" {
            val config = AnalyzerConfiguration()

            config.withPackageManagerOption("Gradle", "gradleVersion", "8.0.2") shouldBe AnalyzerConfiguration(
                packageManagers = mapOf(
                    "Gradle" to PackageManagerConfiguration(
                        options = mapOf("gradleVersion" to "8.0.2")
                    )
                )
            )
        }

        "override an existing option value" {
            val config = AnalyzerConfiguration(
                packageManagers = mapOf(
                    "GradleInspector" to PackageManagerConfiguration(
                        mustRunAfter = listOf("Npm"),
                        options = mapOf("gradleVersion" to "7.6.1", "javaHome" to "/path/to/java/home")
                    ),
                    "Npm" to PackageManagerConfiguration(
                        mustRunAfter = listOf("Yarn")
                    )
                )
            )

            config.withPackageManagerOption("GradleInspector", "gradleVersion", "8.0.2") shouldBe AnalyzerConfiguration(
                packageManagers = mapOf(
                    "GradleInspector" to PackageManagerConfiguration(
                        mustRunAfter = listOf("Npm"),
                        options = mapOf("gradleVersion" to "8.0.2", "javaHome" to "/path/to/java/home")
                    ),
                    "Npm" to PackageManagerConfiguration(
                        mustRunAfter = listOf("Yarn")
                    )
                )
            )
        }

        "merge options for the same package manager with different casing" {
            val config = AnalyzerConfiguration(
                packageManagers = mapOf(
                    "Sbt" to PackageManagerConfiguration(
                        options = mapOf("javaVersion" to "17")
                    )
                )
            )

            config.withPackageManagerOption("SBT", "sbtMode", "true") shouldBe AnalyzerConfiguration(
                packageManagers = mapOf(
                    "SBT" to PackageManagerConfiguration(
                        options = mapOf(
                            "javaVersion" to "17",
                            "sbtMode" to "true"
                        )
                    )
                )
            )
        }
    }
})
