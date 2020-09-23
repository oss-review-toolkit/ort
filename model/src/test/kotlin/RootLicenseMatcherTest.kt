/*
 * Copyright (C) 2020 HERE Europe B.V.
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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.utils.RootLicenseMatcher

class RootLicenseMatcherTest : WordSpec({
    "getApplicableLicenseFilesForDirectories" should {
        "override license files of ancestors as expected" {
            RootLicenseMatcher().getApplicableLicenseFilesForDirectories(
                licenseFindings = licenseFindings(
                    "README",
                    "PATENTS",
                    "a/LICENSE",
                    "a/a/README",
                    "a/a/a/LICENSE",
                    "a/a/a/a/PATENTS"
                ),
                directories = listOf("", "a", "a/a", "a/a/a", "a/a/a/a")
            ).paths() shouldBe mapOf(
                "" to setOf("PATENTS", "README"),
                "a" to setOf("a/LICENSE", "PATENTS"),
                "a/a" to setOf("a/LICENSE", "PATENTS"),
                "a/a/a" to setOf("a/a/a/LICENSE", "PATENTS"),
                "a/a/a/a" to setOf("a/a/a/LICENSE", "a/a/a/a/PATENTS"),
            )
        }

        "not use the readme if there is a license file" {
            RootLicenseMatcher().getApplicableLicenseFilesForDirectories(
                licenseFindings = licenseFindings(
                    "README",
                    "LICENSE"
                ),
                directories = listOf("")
            ).paths() shouldBe mapOf("" to setOf("LICENSE"))
        }

        "use the readme if there is no license but a patents file" {
            RootLicenseMatcher().getApplicableLicenseFilesForDirectories(
                licenseFindings = licenseFindings(
                    "README",
                    "PATENTS"
                ),
                directories = listOf("")
            ).paths() shouldBe mapOf("" to setOf("PATENTS", "README"))
        }
    }
})

private fun licenseFindings(vararg paths: String): List<LicenseFinding> =
    paths.map {
        LicenseFinding(
            license = "LicenseRef-not-relevant",
            location = TextLocation(it, startLine = 1, endLine = 2)
        )
    }

private fun Map<String, Collection<LicenseFinding>>.paths(): Map<String, Set<String>> =
    mapValues { (_, licenseFindings) ->
        licenseFindings.mapTo(mutableSetOf()) { it.location.path }
    }
