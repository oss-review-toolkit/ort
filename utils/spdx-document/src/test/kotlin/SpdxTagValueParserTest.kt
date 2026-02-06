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

package org.ossreviewtoolkit.utils.spdxdocument

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.time.Instant

import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdxdocument.model.SpdxChecksum
import org.ossreviewtoolkit.utils.spdxdocument.model.SpdxRelationship

class SpdxTagValueParserTest : WordSpec({
    "parse" should {
        "parse document-level information" {
            val spdxOutput = """
                SPDXVersion: SPDX-2.1
                DataLicense: CC0-1.0
                SPDXID: SPDXRef-DOCUMENT
                DocumentName: test-project
                DocumentNamespace: https://example.com/test-project
                Creator: Tool: reuse-1.0.0
                Creator: Organization: Example Inc.
                Created: 2024-01-15T10:30:00Z
                Relationship: SPDXRef-DOCUMENT DESCRIBES SPDXRef-0
            """.trimIndent()

            val result = SpdxTagValueParser.parse(spdxOutput)

            result.spdxVersion shouldBe "SPDX-2.1"
            result.dataLicense shouldBe "CC0-1.0"
            result.spdxId shouldBe "SPDXRef-DOCUMENT"
            result.name shouldBe "test-project"
            result.documentNamespace shouldBe "https://example.com/test-project"
            result.creationInfo.creators shouldContainExactly listOf("Tool: reuse-1.0.0", "Organization: Example Inc.")
            result.creationInfo.created shouldBe Instant.parse("2024-01-15T10:30:00Z")
        }

        "parse relationships" {
            val spdxOutput = """
                SPDXVersion: SPDX-2.1
                DataLicense: CC0-1.0
                SPDXID: SPDXRef-DOCUMENT
                DocumentName: test-project
                DocumentNamespace: https://example.com/test
                Relationship: SPDXRef-DOCUMENT DESCRIBES SPDXRef-Package
                Relationship: SPDXRef-Package CONTAINS SPDXRef-File1
                Relationship: SPDXRef-File1 GENERATED_FROM SPDXRef-File2
            """.trimIndent()

            val result = SpdxTagValueParser.parse(spdxOutput)

            result.relationships shouldHaveSize 3
            result.relationships[0].spdxElementId shouldBe "SPDXRef-DOCUMENT"
            result.relationships[0].relationshipType shouldBe SpdxRelationship.Type.DESCRIBES
            result.relationships[0].relatedSpdxElement shouldBe "SPDXRef-Package"
            result.relationships[1].relationshipType shouldBe SpdxRelationship.Type.CONTAINS
            result.relationships[2].relationshipType shouldBe SpdxRelationship.Type.GENERATED_FROM
        }

        "parse a simple SPDX file with one file entry" {
            val spdxOutput = """
                SPDXVersion: SPDX-2.1
                DataLicense: CC0-1.0
                SPDXID: SPDXRef-DOCUMENT
                DocumentName: test-project
                DocumentNamespace: https://example.com/test
                Relationship: SPDXRef-DOCUMENT DESCRIBES SPDXRef-0

                FileName: ./src/main.py
                SPDXID: SPDXRef-0
                FileChecksum: SHA1: abc1234567890abc1234567890abc1234567890a
                LicenseConcluded: MIT
                LicenseInfoInFile: MIT
                FileCopyrightText: Copyright 2024 Example Inc.
            """.trimIndent()

            val result = SpdxTagValueParser.parse(spdxOutput)

            result.files shouldHaveSize 1
            result.files[0].filename shouldBe "src/main.py"
            result.files[0].spdxId shouldBe "SPDXRef-0"
            result.files[0].checksums shouldHaveSize 1
            result.files[0].checksums[0].algorithm shouldBe SpdxChecksum.Algorithm.SHA1
            result.files[0].licenseConcluded shouldBe "MIT"
            result.files[0].licenseInfoInFiles shouldContainExactly listOf("MIT")
            result.files[0].copyrightText shouldBe "Copyright 2024 Example Inc."
        }

        "parse multiple files" {
            val spdxOutput = """
                SPDXVersion: SPDX-2.1
                DataLicense: CC0-1.0
                SPDXID: SPDXRef-DOCUMENT
                DocumentName: test
                DocumentNamespace: https://example.com/test
                Relationship: SPDXRef-DOCUMENT DESCRIBES SPDXRef-0

                FileName: ./file1.py
                SPDXID: SPDXRef-0
                FileChecksum: SHA1: abc1234567890abc1234567890abc1234567890a
                LicenseConcluded: NOASSERTION
                LicenseInfoInFile: MIT
                FileCopyrightText: Copyright 2024 Author1

                FileName: ./file2.py
                SPDXID: SPDXRef-1
                FileChecksum: SHA1: def1234567890def1234567890def1234567890d
                LicenseConcluded: NOASSERTION
                LicenseInfoInFile: Apache-2.0
                FileCopyrightText: Copyright 2024 Author2
            """.trimIndent()

            val result = SpdxTagValueParser.parse(spdxOutput)

            result.files shouldHaveSize 2
            result.files[0].filename shouldBe "file1.py"
            result.files[0].licenseInfoInFiles shouldContainExactly listOf("MIT")
            result.files[1].filename shouldBe "file2.py"
            result.files[1].licenseInfoInFiles shouldContainExactly listOf("Apache-2.0")
        }

        "parse multiple licenses per file" {
            val spdxOutput = """
                SPDXVersion: SPDX-2.1
                DataLicense: CC0-1.0
                SPDXID: SPDXRef-DOCUMENT
                DocumentName: test
                DocumentNamespace: https://example.com/test
                Relationship: SPDXRef-DOCUMENT DESCRIBES SPDXRef-0

                FileName: ./dual-licensed.py
                SPDXID: SPDXRef-0
                FileChecksum: SHA1: abc1234567890abc1234567890abc1234567890a
                LicenseConcluded: NOASSERTION
                LicenseInfoInFile: MIT
                LicenseInfoInFile: Apache-2.0
                LicenseInfoInFile: GPL-3.0-only
                FileCopyrightText: Copyright 2024 Example
            """.trimIndent()

            val result = SpdxTagValueParser.parse(spdxOutput)

            result.files shouldHaveSize 1
            result.files[0].licenseInfoInFiles shouldContainExactlyInAnyOrder
                listOf("MIT", "Apache-2.0", "GPL-3.0-only")
        }

        "parse multi-line copyright text" {
            val spdxOutput = """
                SPDXVersion: SPDX-2.1
                DataLicense: CC0-1.0
                SPDXID: SPDXRef-DOCUMENT
                DocumentName: test
                DocumentNamespace: https://example.com/test
                Relationship: SPDXRef-DOCUMENT DESCRIBES SPDXRef-0

                FileName: ./multi-copyright.py
                SPDXID: SPDXRef-0
                FileChecksum: SHA1: abc1234567890abc1234567890abc1234567890a
                LicenseConcluded: NOASSERTION
                LicenseInfoInFile: MIT
                FileCopyrightText: <text>Copyright 2023 First Author
                Copyright 2024 Second Author
                All rights reserved.</text>
            """.trimIndent()

            val result = SpdxTagValueParser.parse(spdxOutput)

            result.files shouldHaveSize 1
            result.files[0].copyrightText shouldBe
                "Copyright 2023 First Author\nCopyright 2024 Second Author\nAll rights reserved."
        }

        "handle NOASSERTION for license" {
            val spdxOutput = """
                SPDXVersion: SPDX-2.1
                DataLicense: CC0-1.0
                SPDXID: SPDXRef-DOCUMENT
                DocumentName: test
                DocumentNamespace: https://example.com/test
                Relationship: SPDXRef-DOCUMENT DESCRIBES SPDXRef-0

                FileName: ./unknown.py
                SPDXID: SPDXRef-0
                FileChecksum: SHA1: abc1234567890abc1234567890abc1234567890a
                LicenseConcluded: NOASSERTION
                LicenseInfoInFile: NOASSERTION
                FileCopyrightText: Copyright 2024 Example
            """.trimIndent()

            val result = SpdxTagValueParser.parse(spdxOutput)

            result.files shouldHaveSize 1
            result.files[0].licenseInfoInFiles shouldContainExactly listOf(SpdxConstants.NOASSERTION)
        }

        "handle NONE for copyright" {
            val spdxOutput = """
                SPDXVersion: SPDX-2.1
                DataLicense: CC0-1.0
                SPDXID: SPDXRef-DOCUMENT
                DocumentName: test
                DocumentNamespace: https://example.com/test
                Relationship: SPDXRef-DOCUMENT DESCRIBES SPDXRef-0

                FileName: ./no-copyright.py
                SPDXID: SPDXRef-0
                FileChecksum: SHA1: abc1234567890abc1234567890abc1234567890a
                LicenseConcluded: NOASSERTION
                LicenseInfoInFile: MIT
                FileCopyrightText: NONE
            """.trimIndent()

            val result = SpdxTagValueParser.parse(spdxOutput)

            result.files shouldHaveSize 1
            result.files[0].copyrightText shouldBe SpdxConstants.NONE
        }

        "handle single-line text block" {
            val spdxOutput = """
                SPDXVersion: SPDX-2.1
                DataLicense: CC0-1.0
                SPDXID: SPDXRef-DOCUMENT
                DocumentName: test
                DocumentNamespace: https://example.com/test
                Relationship: SPDXRef-DOCUMENT DESCRIBES SPDXRef-0

                FileName: ./inline-text.py
                SPDXID: SPDXRef-0
                FileChecksum: SHA1: abc1234567890abc1234567890abc1234567890a
                LicenseConcluded: NOASSERTION
                LicenseInfoInFile: MIT
                FileCopyrightText: <text>Copyright 2024 Inline Example</text>
            """.trimIndent()

            val result = SpdxTagValueParser.parse(spdxOutput)

            result.files shouldHaveSize 1
            result.files[0].copyrightText shouldBe "Copyright 2024 Inline Example"
        }

        "return empty files list for output without file entries" {
            val spdxOutput = """
                SPDXVersion: SPDX-2.1
                DataLicense: CC0-1.0
                SPDXID: SPDXRef-DOCUMENT
                DocumentName: empty-project
                DocumentNamespace: https://example.com/test
                Relationship: SPDXRef-DOCUMENT DESCRIBES SPDXRef-0
            """.trimIndent()

            val result = SpdxTagValueParser.parse(spdxOutput)

            result.files should beEmpty()
        }

        "normalize file paths by removing leading ./" {
            val spdxOutput = """
                SPDXVersion: SPDX-2.1
                DataLicense: CC0-1.0
                SPDXID: SPDXRef-DOCUMENT
                DocumentName: test
                DocumentNamespace: https://example.com/test
                Relationship: SPDXRef-DOCUMENT DESCRIBES SPDXRef-0

                FileName: ./path/to/file.py
                SPDXID: SPDXRef-0
                FileChecksum: SHA1: abc1234567890abc1234567890abc1234567890a
                LicenseConcluded: NOASSERTION
                LicenseInfoInFile: MIT
                FileCopyrightText: Copyright 2024 Example
            """.trimIndent()

            val result = SpdxTagValueParser.parse(spdxOutput)

            result.files shouldHaveSize 1
            result.files[0].filename shouldBe "path/to/file.py"
        }

        "throw NotImplementedError for package sections" {
            val spdxOutput = """
                SPDXVersion: SPDX-2.1
                DataLicense: CC0-1.0
                SPDXID: SPDXRef-DOCUMENT
                DocumentName: test
                DocumentNamespace: https://example.com/test

                PackageName: test-package
            """.trimIndent()

            shouldThrow<NotImplementedError> {
                SpdxTagValueParser.parse(spdxOutput)
            }
        }

        "throw NotImplementedError for snippet sections" {
            val spdxOutput = """
                SPDXVersion: SPDX-2.1
                DataLicense: CC0-1.0
                SPDXID: SPDXRef-DOCUMENT
                DocumentName: test
                DocumentNamespace: https://example.com/test

                SnippetSPDXID: SPDXRef-Snippet1
            """.trimIndent()

            shouldThrow<NotImplementedError> {
                SpdxTagValueParser.parse(spdxOutput)
            }
        }
    }
})
