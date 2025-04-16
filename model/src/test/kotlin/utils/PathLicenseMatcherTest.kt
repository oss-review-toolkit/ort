/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.model.utils

import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.utils.common.titlecase

private val COMMONLY_USED_LICENSE_FILE_NAMES = listOf(
    "copying",
    "copyright",
    "licence",
    "licence.extension",
    "licencesuffix",
    "license",
    "license.extension",
    "licensesuffix",
    "filename.license",
    "patents",
    "readme",
    "readme.extension",
    "readmesuffix",
    "unlicence",
    "unlicense"
)

class PathLicenseMatcherTest : WordSpec({
    "getApplicableLicenseFilesForDirectories" should {
        "override license files of ancestors as expected" {
            PathLicenseMatcher().getApplicableLicenseFindingsForDirectories(
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
                "a/a/a/a" to setOf("a/a/a/LICENSE", "a/a/a/a/PATENTS")
            )
        }

        "not use the readme if there is a license file" {
            PathLicenseMatcher().getApplicableLicenseFindingsForDirectories(
                licenseFindings = licenseFindings(
                    "README",
                    "LICENSE"
                ),
                directories = listOf("")
            ).paths() shouldBe mapOf("" to setOf("LICENSE"))
        }

        "use the readme if there is no license but a patents file" {
            PathLicenseMatcher().getApplicableLicenseFindingsForDirectories(
                licenseFindings = licenseFindings(
                    "README",
                    "PATENTS"
                ),
                directories = listOf("")
            ).paths() shouldBe mapOf("" to setOf("PATENTS", "README"))
        }

        "match commonly used license file paths in lower-case" {
            COMMONLY_USED_LICENSE_FILE_NAMES.map { it.lowercase() }.forAll {
                PathLicenseMatcher().getApplicableLicenseFindingsForDirectories(
                    licenseFindings = licenseFindings(it),
                    directories = listOf("")
                ).paths() shouldBe mapOf("" to setOf(it))
            }
        }

        "match commonly used license file paths in upper-case" {
            COMMONLY_USED_LICENSE_FILE_NAMES.map { it.uppercase() }.forAll {
                PathLicenseMatcher().getApplicableLicenseFindingsForDirectories(
                    licenseFindings = licenseFindings(it),
                    directories = listOf("")
                ).paths() shouldBe mapOf("" to setOf(it))
            }
        }

        "match commonly used license file paths in capital-case" {
            COMMONLY_USED_LICENSE_FILE_NAMES.map { it.titlecase() }.forAll {
                PathLicenseMatcher().getApplicableLicenseFindingsForDirectories(
                    licenseFindings = licenseFindings(it),
                    directories = listOf("")
                ).paths() shouldBe mapOf("" to setOf(it))
            }
        }
    }
})

private fun licenseFindings(vararg paths: String): List<LicenseFinding> =
    paths.map {
        LicenseFinding(
            license = "LicenseRef-not-relevant",
            location = TextLocation(it, 1, 2)
        )
    }

private fun Map<String, Collection<LicenseFinding>>.paths(): Map<String, Set<String>> =
    mapValues { (_, licenseFindings) ->
        licenseFindings.mapTo(mutableSetOf()) { it.location.path }
    }
