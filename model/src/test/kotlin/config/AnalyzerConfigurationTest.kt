/*
 * Copyright (C) 2022 Bosch.IO GmbH
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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot

import org.ossreviewtoolkit.utils.test.shouldNotBeNull

class AnalyzerConfigurationTest : WordSpec({
    "getPackageManagerConfiguration" should {
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

    "merge" should {
        "overwrite properties with values from other" {
            val self = AnalyzerConfiguration(
                allowDynamicVersions = false,
                enabledPackageManagers = listOf("Gradle"),
                disabledPackageManagers = listOf("NPM"),
                sw360Configuration = sw360Config1
            )

            val other = AnalyzerConfiguration(
                allowDynamicVersions = true,
                enabledPackageManagers = listOf("Maven"),
                disabledPackageManagers = listOf("SBT"),
                sw360Configuration = sw360Config2
            )

            with(self.merge(other)) {
                allowDynamicVersions shouldBe true
                enabledPackageManagers should containExactly("Maven")
                disabledPackageManagers should containExactly("SBT")
                sw360Configuration shouldBe sw360Config2
            }
        }

        "keep values which are null in other" {
            val self = AnalyzerConfiguration(
                enabledPackageManagers = listOf("Gradle"),
                disabledPackageManagers = listOf("NPM"),
                sw360Configuration = sw360Config1
            )

            val other = AnalyzerConfiguration(
                enabledPackageManagers = null,
                disabledPackageManagers = null,
                sw360Configuration = null
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

            val other = AnalyzerConfiguration(
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
                    this should io.kotest.matchers.maps.containExactly(
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

    "AnalyzerConfiguration" should {
        "throw an exception on duplicate package manager configuration" {
            shouldThrow<IllegalArgumentException> {
                AnalyzerConfiguration(
                    packageManagers = mapOf(
                        "Gradle" to PackageManagerConfiguration(),
                        "gradle" to PackageManagerConfiguration()
                    )
                )
            }
        }
    }
})

private val sw360Config1 = Sw360StorageConfiguration(
    restUrl = "url1",
    authUrl = "auth1",
    username = "user1",
    clientId = "client1"
)

private val sw360Config2 = Sw360StorageConfiguration(
    restUrl = "url2",
    authUrl = "auth2",
    username = "user2",
    clientId = "client2"
)
