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
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.shouldNot

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
