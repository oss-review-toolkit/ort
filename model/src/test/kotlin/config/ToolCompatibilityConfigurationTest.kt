/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.model.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec

class ToolCompatibilityConfigurationTest : WordSpec({
    "Initialization" should {
        "fail if both a semantic version spec and a calendar version spec are specified" {
            shouldThrow<IllegalArgumentException> {
                ToolCompatibilityConfiguration(semanticVersionSpec = "1.2.3", calendarVersionSpec = "2021.3.5")
            }
        }

        "fail if both a semantic version spec and a version pattern are specified" {
            shouldThrow<IllegalArgumentException> {
                ToolCompatibilityConfiguration(semanticVersionSpec = "1.2.3", versionPattern = ".*test.*")
            }
        }

        "fail if both a calendar version spec and a version pattern are specified" {
            shouldThrow<IllegalArgumentException> {
                ToolCompatibilityConfiguration(calendarVersionSpec = "1.2.3", versionPattern = "somePattern")
            }
        }
    }
})
