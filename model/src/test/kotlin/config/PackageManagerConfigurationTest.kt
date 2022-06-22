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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.should

import org.ossreviewtoolkit.utils.test.shouldNotBeNull

class PackageManagerConfigurationTest : WordSpec({
    "merge" should {
        "prioritize mustRunAfter from other" {
            val self = PackageManagerConfiguration(mustRunAfter = listOf("Gradle"))
            val other = PackageManagerConfiguration(mustRunAfter = listOf("NPM"))

            self.merge(other).mustRunAfter shouldNotBeNull {
                this should containExactly("NPM")
            }
        }

        "keep mustRunAfter if it is null in other" {
            val self = PackageManagerConfiguration(mustRunAfter = listOf("Gradle"))
            val other = PackageManagerConfiguration(mustRunAfter = null)

            self.merge(other).mustRunAfter shouldNotBeNull {
                this should containExactly("Gradle")
            }
        }

        "overwrite options with values from other" {
            val self = PackageManagerConfiguration(options = mapOf("option" to "value1"))
            val other = PackageManagerConfiguration(options = mapOf("option" to "value2"))

            self.merge(other).options shouldNotBeNull {
                this should io.kotest.matchers.maps.containExactly("option" to "value2")
            }
        }

        "keep options which are not contained in other" {
            val self = PackageManagerConfiguration(options = mapOf("option1" to "value1"))
            val other = PackageManagerConfiguration(options = mapOf("option2" to "value2"))

            self.merge(other).options shouldNotBeNull {
                this should io.kotest.matchers.maps.containExactly("option1" to "value1", "option2" to "value2")
            }
        }
    }
})
