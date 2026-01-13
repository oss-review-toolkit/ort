/*
 * Copyright (C) 2017 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.scanners.reuse

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

import java.time.Instant

import org.ossreviewtoolkit.model.CopyrightFinding
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.TextLocation

class ReuseTest : WordSpec({
    "configuration" should {
        "be empty when no options are enabled" {
            val config = createConfig()

            val reuse = Reuse(config = config)

            reuse.configuration shouldBe ""
        }

        "include --include-submodules when enabled" {
            val config = createConfig(includeSubmodules = true)

            val reuse = Reuse(config = config)

            reuse.configuration shouldBe "--include-submodules"
        }

        "include --include-meson-subprojects when enabled" {
            val config = createConfig(includeMesonSubprojects = true)

            val reuse = Reuse(config = config)

            reuse.configuration shouldBe "--include-meson-subprojects"
        }

        "include both options when both are enabled" {
            val config = createConfig(includeSubmodules = true, includeMesonSubprojects = true)

            val reuse = Reuse(config = config)

            reuse.configuration shouldBe "--include-submodules --include-meson-subprojects"
        }
    }

    "createSummary" should {
        "extract license findings from SPDX output" {
            val config = createConfig()
            val reuse = Reuse(config = config)
            val spdxOutput = """
                SPDXVersion: SPDX-2.1
                DataLicense: CC0-1.0
                SPDXID: SPDXRef-DOCUMENT
                DocumentName: test
                DocumentNamespace: https://example.com/test
                Relationship: SPDXRef-DOCUMENT DESCRIBES SPDXRef-0

                FileName: ./src/main.py
                SPDXID: SPDXRef-0
                LicenseInfoInFile: MIT
                FileCopyrightText: Copyright 2024 Example

                FileName: ./lib/utils.py
                SPDXID: SPDXRef-1
                LicenseInfoInFile: Apache-2.0
                FileCopyrightText: Copyright 2024 Example
            """.trimIndent()
            val startTime = Instant.now()
            val endTime = Instant.now()

            val summary = reuse.createSummary(spdxOutput, startTime, endTime)

            summary.licenseFindings shouldHaveSize 2
            summary.licenseFindings shouldContainExactlyInAnyOrder listOf(
                LicenseFinding("MIT", TextLocation("src/main.py", TextLocation.UNKNOWN_LINE)),
                LicenseFinding("Apache-2.0", TextLocation("lib/utils.py", TextLocation.UNKNOWN_LINE))
            )
        }

        "extract multiple licenses per file" {
            val config = createConfig()
            val reuse = Reuse(config = config)
            val spdxOutput = """
                SPDXVersion: SPDX-2.1
                DataLicense: CC0-1.0
                SPDXID: SPDXRef-DOCUMENT
                DocumentName: test
                DocumentNamespace: https://example.com/test
                Relationship: SPDXRef-DOCUMENT DESCRIBES SPDXRef-0

                FileName: ./dual.py
                SPDXID: SPDXRef-0
                LicenseInfoInFile: MIT
                LicenseInfoInFile: Apache-2.0
                FileCopyrightText: Copyright 2024 Example
            """.trimIndent()
            val startTime = Instant.now()
            val endTime = Instant.now()

            val summary = reuse.createSummary(spdxOutput, startTime, endTime)

            summary.licenseFindings shouldHaveSize 2
            summary.licenseFindings shouldContainExactlyInAnyOrder listOf(
                LicenseFinding("MIT", TextLocation("dual.py", TextLocation.UNKNOWN_LINE)),
                LicenseFinding("Apache-2.0", TextLocation("dual.py", TextLocation.UNKNOWN_LINE))
            )
        }

        "extract copyright findings from SPDX output" {
            val config = createConfig()
            val reuse = Reuse(config = config)
            val spdxOutput = """
                SPDXVersion: SPDX-2.1
                DataLicense: CC0-1.0
                SPDXID: SPDXRef-DOCUMENT
                DocumentName: test
                DocumentNamespace: https://example.com/test
                Relationship: SPDXRef-DOCUMENT DESCRIBES SPDXRef-0

                FileName: ./src/main.py
                SPDXID: SPDXRef-0
                LicenseInfoInFile: MIT
                FileCopyrightText: Copyright 2024 Example Inc.
            """.trimIndent()
            val startTime = Instant.now()
            val endTime = Instant.now()

            val summary = reuse.createSummary(spdxOutput, startTime, endTime)

            summary.copyrightFindings shouldHaveSize 1
            summary.copyrightFindings shouldContainExactlyInAnyOrder listOf(
                CopyrightFinding(
                    "Copyright 2024 Example Inc.",
                    TextLocation("src/main.py", TextLocation.UNKNOWN_LINE)
                )
            )
        }

        "split multi-line copyright text into separate findings" {
            val config = createConfig()
            val reuse = Reuse(config = config)
            val spdxOutput = """
                SPDXVersion: SPDX-2.1
                DataLicense: CC0-1.0
                SPDXID: SPDXRef-DOCUMENT
                DocumentName: test
                DocumentNamespace: https://example.com/test
                Relationship: SPDXRef-DOCUMENT DESCRIBES SPDXRef-0

                FileName: ./multi.py
                SPDXID: SPDXRef-0
                LicenseInfoInFile: MIT
                FileCopyrightText: <text>Copyright 2023 First Author
                Copyright 2024 Second Author</text>
            """.trimIndent()
            val startTime = Instant.now()
            val endTime = Instant.now()

            val summary = reuse.createSummary(spdxOutput, startTime, endTime)

            summary.copyrightFindings shouldHaveSize 2
            summary.copyrightFindings shouldContainExactlyInAnyOrder listOf(
                CopyrightFinding(
                    "Copyright 2023 First Author",
                    TextLocation("multi.py", TextLocation.UNKNOWN_LINE)
                ),
                CopyrightFinding(
                    "Copyright 2024 Second Author",
                    TextLocation("multi.py", TextLocation.UNKNOWN_LINE)
                )
            )
        }

        "not include copyright findings for NOASSERTION" {
            val config = createConfig()
            val reuse = Reuse(config = config)
            val spdxOutput = """
                SPDXVersion: SPDX-2.1
                DataLicense: CC0-1.0
                SPDXID: SPDXRef-DOCUMENT
                DocumentName: test
                DocumentNamespace: https://example.com/test
                Relationship: SPDXRef-DOCUMENT DESCRIBES SPDXRef-0

                FileName: ./no-copyright.py
                SPDXID: SPDXRef-0
                LicenseInfoInFile: MIT
                FileCopyrightText: NOASSERTION
            """.trimIndent()
            val startTime = Instant.now()
            val endTime = Instant.now()

            val summary = reuse.createSummary(spdxOutput, startTime, endTime)

            summary.copyrightFindings shouldHaveSize 0
        }
    }
})

private fun createConfig(
    includeSubmodules: Boolean = false,
    includeMesonSubprojects: Boolean = false,
    readFromStorage: Boolean = true,
    writeToStorage: Boolean = true
) = ReuseConfig(
    includeSubmodules = includeSubmodules,
    includeMesonSubprojects = includeMesonSubprojects,
    regScannerName = null,
    minVersion = null,
    maxVersion = null,
    configuration = null,
    readFromStorage = readFromStorage,
    writeToStorage = writeToStorage
)
