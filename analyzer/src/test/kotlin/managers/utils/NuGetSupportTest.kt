/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

package org.ossreviewtoolkit.analyzer.managers.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import java.time.Instant

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.Scope

class NuGetSupportTest : StringSpec({
    "non-existing project gets registered as error and is not added to scope" {
        val testPackage = Identifier.EMPTY.copy(name = "trifj", version = "2.0.0")
        val testPackage2 = Identifier.EMPTY.copy(name = "tffrifj", version = "2.0.0")
        val support = NuGetSupport(setOf(testPackage, testPackage2))
        val resultScope = Scope("dependencies", sortedSetOf())
        val resultErrors = listOf(
            OrtIssue(
                timestamp = Instant.EPOCH,
                source = "nuget-API",
                message = "${testPackage.name}:${testPackage.version} can not be found on Nugets RestAPI."
            ),
            OrtIssue(
                timestamp = Instant.EPOCH,
                source = "nuget-API",
                message = "${testPackage2.name}:${testPackage2.version} can not be found on Nugets RestAPI."
            )
        )

        support.scope shouldBe resultScope
        support.issues.map { it.copy(timestamp = Instant.EPOCH) } shouldBe resultErrors
    }
})
