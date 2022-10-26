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

package org.ossreviewtoolkit.utils.common

import io.kotest.core.spec.style.StringSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.maps.containExactly
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beTheSameInstanceAs

class EnvironmentVariableFilterTest : StringSpec({
    afterAny {
        // Reset default values after each test.
        EnvironmentVariableFilter.reset()
    }

    "Uncritical variables should be accepted" {
        listOf("FOO", "bar", "boring", "uncritical", "PATH").forAll { variable ->
            EnvironmentVariableFilter.isAllowed(variable) shouldBe true
        }
    }

    "Variables should be filtered based on default deny substrings" {
        listOf("DATABASE_PASSWORD", "repositoryToken", "service-user", "APIKEY").forAll { variable ->
            EnvironmentVariableFilter.isAllowed(variable) shouldBe false
        }
    }

    "Variables should be accepted if they are on the allowed list" {
        listOf("USER", "USERPROFILE", "GRADLE_USER_HOME", "GIT_HTTP_USER_AGENT").forAll { variable ->
            EnvironmentVariableFilter.isAllowed(variable) shouldBe true
        }
    }

    "Denied substrings can be changed" {
        EnvironmentVariableFilter.reset(listOf("foo"))

        EnvironmentVariableFilter.isAllowed("ENV_FOO") shouldBe false
        EnvironmentVariableFilter.isAllowed("MY_PASSWORD") shouldBe true
    }

    "Allowed names can be changed" {
        val allowedSecret = "MAVEN_REPO_PASSWORD"

        EnvironmentVariableFilter.reset(allowNames = setOf(allowedSecret))

        EnvironmentVariableFilter.isAllowed(allowedSecret) shouldBe true
    }

    "The environment map can be filtered" {
        val environment = mutableMapOf(
            "USER" to "scott",
            "password" to "tiger",
            "foo" to "bar"
        )
        val filteredEnvironment = mapOf(
            "USER" to "scott",
            "foo" to "bar"
        )

        EnvironmentVariableFilter.filter(environment) should beTheSameInstanceAs(environment)

        environment should containExactly(filteredEnvironment)
    }
})
