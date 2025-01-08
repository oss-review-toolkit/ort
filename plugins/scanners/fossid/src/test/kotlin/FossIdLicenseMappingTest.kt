/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.scanners.fossid

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith

import kotlinx.coroutines.flow.flowOf

import org.ossreviewtoolkit.clients.fossid.model.result.LicenseCategory
import org.ossreviewtoolkit.clients.fossid.model.result.MatchType
import org.ossreviewtoolkit.clients.fossid.model.result.Snippet
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.toSpdx

private const val FILE_PATH = "filePath"
private const val FILE_PATH_SNIPPET = "filePathSnippet"

class FossIdLicenseMappingTest : WordSpec({
    "FossIdScanResults" should {
        "create an issue when a license in an identified file cannot be mapped" {
            val sampleFile = createMarkAsIdentifiedFile("invalid license", FILE_PATH)
            val issues = mutableListOf<Issue>()

            val findings = listOf(sampleFile).mapSummary(emptyMap(), issues, emptyMap())

            issues should haveSize(1)
            issues.first() shouldNotBeNull {
                message shouldStartWith "Failed to parse license 'invalid license' as an SPDX expression:"
            }

            findings.licenseFindings should beEmpty()
        }

        "map non-SPDX compliant FossID licenses in a snippet to snippet findings" {
            val rawResults = createSnippet("Apache 2.0")
            val issues = mutableListOf<Issue>()

            val mapping = mapOf("Apache 2.0" to "Apache-2.0")
            val findings = mapSnippetFindings(
                rawResults,
                500,
                issues,
                mapping,
                emptyList(),
                mutableSetOf()
            )

            issues should beEmpty()
            findings should haveSize(1)
            findings.first() shouldNotBeNull {
                snippets.first() shouldNotBeNull {
                    license.toString() shouldBe "Apache-2.0"
                }
            }
        }

        "map deprecated SPDX FossID licenses in a snippet to snippet findings" {
            val rawResults = createSnippet("GFDL-1.2")
            val issues = mutableListOf<Issue>()

            val findings = mapSnippetFindings(
                rawResults,
                500,
                issues,
                emptyMap(),
                emptyList(),
                mutableSetOf()
            )

            issues should beEmpty()
            findings should haveSize(1)
            findings.first() shouldNotBeNull {
                snippets.first() shouldNotBeNull {
                    license.toString() shouldBe "GFDL-1.2-only"
                }
            }
        }

        "map SPDX compliant FossID licenses in a snippet to snippet findings" {
            val rawResults = createSnippet("Apache-2.0")
            val issues = mutableListOf<Issue>()

            val findings = mapSnippetFindings(
                rawResults,
                500,
                issues,
                emptyMap(),
                emptyList(),
                mutableSetOf()
            )

            issues should beEmpty()
            findings should haveSize(1)
            findings.first() shouldNotBeNull {
                snippets.first() shouldNotBeNull {
                    license.toString() shouldBe "Apache-2.0"
                }
            }
        }

        "create an issue when a license in a snippet cannot be mapped" {
            val rawResults = createSnippet("invalid license")
            val issues = mutableListOf<Issue>()

            val findings = mapSnippetFindings(
                rawResults,
                500,
                issues,
                emptyMap(),
                emptyList(),
                mutableSetOf()
            )

            issues should haveSize(1)
            issues.first() shouldNotBeNull {
                message shouldStartWith
                    "Failed to parse license 'invalid license' as an SPDX expression"
                severity shouldBe Severity.ERROR
            }

            findings should haveSize(1)
            findings.first() shouldNotBeNull {
                snippets.first() shouldNotBeNull {
                    license shouldBe SpdxConstants.NOASSERTION.toSpdx()
                }
            }
        }
    }
})

private fun createSnippet(license: String): RawResults {
    val snippet = Snippet(
        1,
        "created",
        1,
        1,
        1,
        MatchType.PARTIAL,
        null,
        "author",
        "artifact",
        "version",
        "pkg:maven/com.vdurmont/semver4j@3.1.0",
        license,
        LicenseCategory.UNCATEGORIZED,
        "releaseDate",
        null,
        FILE_PATH_SNIPPET,
        null,
        "url",
        null,
        null,
        null,
        null,
        "1.0",
        null,
        null,
        null
    )
    return RawResults(emptyList(), emptyList(), emptyList(), emptyList(), flowOf(FILE_PATH to setOf(snippet)))
}
