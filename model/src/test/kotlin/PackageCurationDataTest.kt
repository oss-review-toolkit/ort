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

package org.ossreviewtoolkit.model

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.utils.ort.ProcessedDeclaredLicense
import org.ossreviewtoolkit.utils.spdx.toSpdx

class PackageCurationDataTest : WordSpec({
    val original = PackageCurationData(
        comment = "original",
        purl = "original",
        cpe = "original",
        authors = setOf("original"),
        concludedLicense = "original".toSpdx(),
        description = "original",
        homepageUrl = "original",
        binaryArtifact = RemoteArtifact(
            url = "original",
            hash = Hash.create("original")
        ),
        sourceArtifact = RemoteArtifact(
            url = "original",
            hash = Hash.create("original")
        ),
        vcs = VcsInfoCurationData(
            type = VcsType.GIT,
            url = "original",
            revision = "original",
            path = "original"
        ),
        isMetadataOnly = true,
        isModified = true,
        declaredLicenseMapping = mapOf("original" to "original".toSpdx())
    )

    val other = PackageCurationData(
        comment = "other",
        purl = "other",
        cpe = "other",
        authors = setOf("other"),
        concludedLicense = "other".toSpdx(),
        description = "other",
        homepageUrl = "other",
        binaryArtifact = RemoteArtifact(
            url = "other",
            hash = Hash.create("other")
        ),
        sourceArtifact = RemoteArtifact(
            url = "other",
            hash = Hash.create("other")
        ),
        vcs = VcsInfoCurationData(
            type = VcsType.SUBVERSION,
            url = "other",
            revision = "other",
            path = "other"
        ),
        isMetadataOnly = false,
        isModified = false,
        declaredLicenseMapping = mapOf("other" to "other".toSpdx())
    )

    "Merging" should {
        "replace all unset data" {
            PackageCurationData().merge(other) shouldBe other
        }

        "replace unset original data" {
            val originalWithSomeUnsetData = original.copy(
                comment = null,
                authors = null,
                concludedLicense = null,
                binaryArtifact = null,
                vcs = null,
                isMetadataOnly = null,
                declaredLicenseMapping = emptyMap()
            )

            originalWithSomeUnsetData.merge(other) shouldBe originalWithSomeUnsetData.copy(
                comment = other.comment,
                authors = other.authors,
                concludedLicense = other.concludedLicense,
                binaryArtifact = other.binaryArtifact,
                vcs = other.vcs,
                isMetadataOnly = other.isMetadataOnly,
                declaredLicenseMapping = other.declaredLicenseMapping
            )
        }

        "keep existing original data" {
            original.merge(other) shouldBe original.copy(
                comment = "original\nother",
                authors = setOf("original", "other"),
                concludedLicense = "original AND other".toSpdx(),
                declaredLicenseMapping = mapOf(
                    "original" to "original".toSpdx(),
                    "other" to "other".toSpdx()
                )
            )
        }

        "not keep duplicate data" {
            val otherWithSomeOriginalData = other.copy(
                comment = original.comment,
                authors = original.authors,
                concludedLicense = original.concludedLicense,
                declaredLicenseMapping = original.declaredLicenseMapping
            )

            val mergedData = original.merge(otherWithSomeOriginalData)

            mergedData shouldBe original
            mergedData.concludedLicense.toString() shouldBe original.concludedLicense.toString()
            mergedData.declaredLicenseMapping.values.map { it.toString() } shouldBe original.declaredLicenseMapping
                .values.map { it.toString() }
        }

        "merge nested VCS information" {
            val originalWithPartialVcsData = original.copy(vcs = original.vcs?.copy(path = null))

            originalWithPartialVcsData.merge(other).vcs shouldBe original.vcs?.copy(path = other.vcs?.path)
        }
    }

    "Applying" should {
        "preserve the original SPDX operator" {
            val curatedPackage = CuratedPackage(
                metadata = Package(
                    id = Identifier("Maven", "namespace", "name", "0.0.1"),
                    declaredLicenses = setOf("Apache-2.0", "LGPL-2.1-or-later"),
                    declaredLicensesProcessed = ProcessedDeclaredLicense(
                        spdxExpression = "Apache-2.0 OR LGPL-2.1-or-later".toSpdx(),
                    ),
                    description = "original",
                    homepageUrl = "original",
                    binaryArtifact = RemoteArtifact.EMPTY,
                    sourceArtifact = RemoteArtifact.EMPTY,
                    vcs = VcsInfo.EMPTY
                ),
                curations = listOf(
                    PackageCurationResult(
                        base = PackageCurationData(),
                        curation = PackageCurationData(
                            concludedLicense = "Apache-2.0 OR LGPL-2.1-or-later".toSpdx()
                        )
                    )
                )
            )

            val result = original.apply(curatedPackage)
            result.metadata.declaredLicensesProcessed.spdxExpression shouldBe "Apache-2.0 OR LGPL-2.1-or-later".toSpdx()
        }

        "preserve the original operator for a complex SPDX compound expression" {
            val curatedPackage = CuratedPackage(
                metadata = Package(
                    id = Identifier("Maven", "namespace", "name", "0.0.1"),
                    declaredLicenses = setOf("Apache-2.0", "LGPL-2.1-or-later", "MIT"),
                    declaredLicensesProcessed = ProcessedDeclaredLicense(
                        spdxExpression = "(Apache-2.0 OR LGPL-2.1-or-later) AND MIT".toSpdx(),
                    ),
                    description = "original",
                    homepageUrl = "original",
                    binaryArtifact = RemoteArtifact.EMPTY,
                    sourceArtifact = RemoteArtifact.EMPTY,
                    vcs = VcsInfo.EMPTY
                ),
                curations = listOf(
                    PackageCurationResult(
                        base = PackageCurationData(),
                        curation = PackageCurationData(
                            concludedLicense = "(Apache-2.0 OR LGPL-2.1-or-later) AND MIT".toSpdx()
                        )
                    )
                )
            )
            val result = original.apply(curatedPackage)
            result.metadata.declaredLicensesProcessed.spdxExpression
                .shouldBe("(Apache-2.0 OR LGPL-2.1-or-later) AND MIT".toSpdx())
        }
    }
})
