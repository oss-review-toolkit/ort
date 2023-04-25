/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.maps.beEmpty
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.utils.spdx.toSpdx

class PackageCurationTest : WordSpec({
    "Applying a single curation" should {
        "overwrite the correct values" {
            val pkg = Package(
                id = Identifier(
                    type = "Maven",
                    namespace = "org.hamcrest",
                    name = "hamcrest-core",
                    version = "1.3"
                ),
                authors = emptySet(),
                declaredLicenses = setOf("license a", "license b"),
                description = "",
                homepageUrl = "",
                binaryArtifact = RemoteArtifact.EMPTY,
                sourceArtifact = RemoteArtifact.EMPTY,
                vcs = VcsInfo.EMPTY,
                isMetadataOnly = false,
                isModified = false
            )

            val curation = PackageCuration(
                id = pkg.id,
                data = PackageCurationData(
                    purl = "pkg:maven/org.hamcrest/hamcrest-core@1.3#subpath=src/main/java/org/hamcrest/core",
                    cpe = "cpe:2.3:a:apache:commons_io:2.8.0:rc2:*:*:*:*:*:*",
                    authors = setOf("author 1", "author 2"),
                    declaredLicenseMapping = mapOf("license a" to "Apache-2.0".toSpdx()),
                    concludedLicense = "license1 OR license2".toSpdx(),
                    description = "description",
                    homepageUrl = "http://home.page",
                    binaryArtifact = RemoteArtifact(
                        url = "http://binary.artifact",
                        hash = Hash.create("binary.hash")
                    ),
                    sourceArtifact = RemoteArtifact(
                        url = "http://source.artifact",
                        hash = Hash.create("source.hash")
                    ),
                    vcs = VcsInfoCurationData(
                        type = VcsType.GIT,
                        url = "http://url.git",
                        revision = "revision",
                        path = "path"
                    ),
                    isMetadataOnly = true,
                    isModified = true
                )
            )

            val curatedPkg = curation.apply(pkg.toCuratedPackage())

            with(curatedPkg.metadata) {
                id.toCoordinates() shouldBe pkg.id.toCoordinates()
                purl shouldBe curation.data.purl
                cpe shouldBe curation.data.cpe
                authors shouldBe curation.data.authors
                declaredLicenses shouldBe pkg.declaredLicenses
                declaredLicensesProcessed.spdxExpression shouldBe "Apache-2.0".toSpdx()
                declaredLicensesProcessed.unmapped should containExactly("license b")
                concludedLicense shouldBe curation.data.concludedLicense
                description shouldBe curation.data.description
                homepageUrl shouldBe curation.data.homepageUrl
                binaryArtifact shouldBe curation.data.binaryArtifact
                sourceArtifact shouldBe curation.data.sourceArtifact
                vcs shouldBe pkg.vcs
                vcsProcessed.toCuration() shouldBe curation.data.vcs
                isMetadataOnly shouldBe true
                isModified shouldBe true
            }

            curatedPkg.curations.size shouldBe 1
            curatedPkg.curations.first().base shouldBe pkg.diff(curatedPkg.metadata)
            curatedPkg.curations.first().curation shouldBe curation.data
        }

        "change only curated fields" {
            val pkg = Package(
                id = Identifier(
                    type = "Maven",
                    namespace = "org.hamcrest",
                    name = "hamcrest-core",
                    version = "1.3"
                ),
                cpe = "cpe:2.3:a:apache:commons_io:2.8.0:rc2:*:*:*:*:*:*",
                authors = setOf("author 1", "author 2"),
                declaredLicenses = setOf("license a", "license b"),
                description = "description",
                homepageUrl = "homepageUrl",
                binaryArtifact = RemoteArtifact.EMPTY,
                sourceArtifact = RemoteArtifact.EMPTY,
                vcs = VcsInfo(
                    type = VcsType.GIT,
                    url = "http://url.git",
                    revision = "revision",
                    path = "path"
                ),
                isMetadataOnly = false,
                isModified = false
            )

            val curation = PackageCuration(
                id = pkg.id,
                data = PackageCurationData(
                    homepageUrl = "http://home.page",
                    vcs = VcsInfoCurationData(
                        url = "http://url.git"
                    )
                )
            )

            val curatedPkg = curation.apply(pkg.toCuratedPackage())

            with(curatedPkg.metadata) {
                id.toCoordinates() shouldBe pkg.id.toCoordinates()
                purl shouldBe pkg.purl
                cpe shouldBe pkg.cpe
                authors shouldBe pkg.authors
                declaredLicenses shouldBe pkg.declaredLicenses
                concludedLicense shouldBe pkg.concludedLicense
                description shouldBe pkg.description
                homepageUrl shouldBe curation.data.homepageUrl
                binaryArtifact shouldBe pkg.binaryArtifact
                sourceArtifact shouldBe pkg.sourceArtifact
                vcs shouldBe VcsInfo(
                    type = pkg.vcs.type,
                    url = curation.data.vcs!!.url!!,
                    revision = pkg.vcs.revision,
                    path = pkg.vcs.path
                )
                isMetadataOnly shouldBe false
                isModified shouldBe false
            }

            curatedPkg.curations.size shouldBe 1
            curatedPkg.curations.first().base shouldBe pkg.diff(curatedPkg.metadata)
            curatedPkg.curations.first().curation shouldBe curation.data
        }

        "be able to empty VCS information" {
            val pkg = Package(
                id = Identifier(
                    type = "Maven",
                    namespace = "org.hamcrest",
                    name = "hamcrest-core",
                    version = "1.3"
                ),
                authors = setOf("author 1", "author 2"),
                declaredLicenses = setOf("license a", "license b"),
                description = "description",
                homepageUrl = "homepageUrl",
                binaryArtifact = RemoteArtifact.EMPTY,
                sourceArtifact = RemoteArtifact.EMPTY,
                vcs = VcsInfo(
                    type = VcsType.GIT,
                    url = "http://url.git",
                    revision = "revision",
                    path = "path"
                )
            )

            val curation = PackageCuration(
                id = pkg.id,
                data = PackageCurationData(
                    vcs = VcsInfoCurationData(
                        type = VcsType.UNKNOWN,
                        url = "",
                        revision = "",
                        path = ""
                    )
                )
            )

            val curatedPkg = curation.apply(pkg.toCuratedPackage())

            curatedPkg.curations.size shouldBe 1
            curatedPkg.metadata.vcsProcessed shouldBe VcsInfo.EMPTY
        }

        "fail if identifiers do not match" {
            val pkg = Package(
                id = Identifier(
                    type = "Maven",
                    namespace = "org.hamcrest",
                    name = "hamcrest-core",
                    version = "1.3"
                ),
                authors = emptySet(),
                declaredLicenses = emptySet(),
                description = "",
                homepageUrl = "",
                binaryArtifact = RemoteArtifact.EMPTY,
                sourceArtifact = RemoteArtifact.EMPTY,
                vcs = VcsInfo.EMPTY
            )

            val curation = PackageCuration(
                id = Identifier.EMPTY,
                data = PackageCurationData(
                    homepageUrl = "http://home.page",
                    vcs = VcsInfoCurationData(
                        type = VcsType.UNKNOWN,
                        url = "http://url.git",
                        revision = ""
                    )
                )
            )

            shouldThrow<IllegalArgumentException> {
                curation.apply(pkg.toCuratedPackage())
            }
        }

        "be able to clear isMetadataOnly" {
            val pkg = Package(
                id = Identifier(
                    type = "Maven",
                    namespace = "org.hamcrest",
                    name = "hamcrest-core",
                    version = "1.3"
                ),
                authors = emptySet(),
                declaredLicenses = emptySet(),
                description = "",
                homepageUrl = "",
                binaryArtifact = RemoteArtifact.EMPTY,
                sourceArtifact = RemoteArtifact.EMPTY,
                vcs = VcsInfo.EMPTY,
                isMetadataOnly = true
            )

            val curation = PackageCuration(
                id = pkg.id,
                data = PackageCurationData(
                    isMetadataOnly = false
                )
            )

            val curatedPkg = curation.apply(pkg.toCuratedPackage())

            curatedPkg.metadata.isMetadataOnly shouldBe false
        }

        "be able to clear isModified" {
            val pkg = Package(
                id = Identifier(
                    type = "Maven",
                    namespace = "org.hamcrest",
                    name = "hamcrest-core",
                    version = "1.3"
                ),
                authors = emptySet(),
                declaredLicenses = emptySet(),
                description = "",
                homepageUrl = "",
                binaryArtifact = RemoteArtifact.EMPTY,
                sourceArtifact = RemoteArtifact.EMPTY,
                vcs = VcsInfo.EMPTY,
                isModified = true
            )

            val curation = PackageCuration(
                id = pkg.id,
                data = PackageCurationData(
                    isModified = false
                )
            )

            val curatedPkg = curation.apply(pkg.toCuratedPackage())

            curatedPkg.metadata.isModified shouldBe false
        }

        "work with Ivy version ranges" {
            val pkgVersionInsideRange = Package.EMPTY.copy(
                id = Identifier(
                    type = "Maven",
                    namespace = "androidx.constraintlayout",
                    name = "constraintlayout",
                    version = "2.0.3"
                )
            )

            val pkgVersionOutsideRange = Package.EMPTY.copy(
                id = Identifier(
                    type = "Maven",
                    namespace = "androidx.constraintlayout",
                    name = "constraintlayout",
                    version = "2.0.5"
                )
            )

            val curation = PackageCuration(
                id = Identifier(
                    type = "Maven",
                    namespace = "androidx.constraintlayout",
                    name = "constraintlayout",
                    version = "[2.0.1,2.0.4]"
                ),
                data = PackageCurationData()
            )

            curation.isApplicable(pkgVersionInsideRange.id) shouldBe true
            curation.isApplicable(pkgVersionOutsideRange.id) shouldBe false
        }
    }

    "Applying multiple curations" should {
        "accumulate curation results to the curated package" {
            val id = Identifier("type", "namespace", "name", "version")
            val pkg = Package.EMPTY.copy(id = id)
            val curation1 = PackageCuration(id, PackageCurationData(description = "description 1"))
            val curation2 = PackageCuration(id, PackageCurationData(description = "description 2"))
            val curation3 = PackageCuration(id, PackageCurationData(description = "description 3"))

            val result1 = curation1.apply(pkg.toCuratedPackage())
            val result2 = curation2.apply(result1)
            val result3 = curation3.apply(result2)

            result1.metadata.description shouldBe "description 1"
            result1.curations.size shouldBe 1
            result1.curations[0].base shouldBe PackageCurationData(description = "")
            result1.curations[0].curation shouldBe curation1.data

            result2.metadata.description shouldBe "description 2"
            result2.curations.size shouldBe 2
            result2.curations[0].base shouldBe PackageCurationData(description = "")
            result2.curations[0].curation shouldBe curation1.data
            result2.curations[1].base shouldBe PackageCurationData(description = "description 1")
            result2.curations[1].curation shouldBe curation2.data

            result3.metadata.description shouldBe "description 3"
            result3.curations.size shouldBe 3
            result3.curations[0].base shouldBe PackageCurationData(description = "")
            result3.curations[0].curation shouldBe curation1.data
            result3.curations[1].base shouldBe PackageCurationData(description = "description 1")
            result3.curations[1].curation shouldBe curation2.data
            result3.curations[2].base shouldBe PackageCurationData(description = "description 2")
            result3.curations[2].curation shouldBe curation3.data
        }
    }

    "Applying multiple declared license mapping curations" should {
        "accumulate the map entries and override the entries with same key" {
            val pkg = Package(
                id = Identifier("type", "namespace", "name", "version"),
                authors = emptySet(),
                declaredLicenses = setOf("license a", "license b", "license c"),
                description = "",
                homepageUrl = "",
                binaryArtifact = RemoteArtifact.EMPTY,
                sourceArtifact = RemoteArtifact.EMPTY,
                vcs = VcsInfo.EMPTY
            )

            val curation1 =
                declaredLicenseMappingCuration(pkg.id, "license a" to "Apache-2.0", "license b" to "BSD-3-Clause")
            val curation2 = declaredLicenseMappingCuration(pkg.id, "license c" to "CC-BY-1.0")
            val curation3 = declaredLicenseMappingCuration(pkg.id, "license c" to "CC-BY-2.0")

            val result1 = curation1.apply(pkg.toCuratedPackage())
            val result2 = curation2.apply(result1)
            val result3 = curation3.apply(result2)

            result1.metadata.declaredLicensesProcessed.spdxExpression shouldBe
                    "Apache-2.0 AND BSD-3-Clause".toSpdx()
            result2.metadata.declaredLicensesProcessed.spdxExpression shouldBe
                    "Apache-2.0 AND BSD-3-Clause AND CC-BY-1.0".toSpdx()
            result3.metadata.declaredLicensesProcessed.spdxExpression shouldBe
                    "Apache-2.0 AND BSD-3-Clause AND CC-BY-2.0".toSpdx()

            result3.curations[0].base.declaredLicenseMapping should beEmpty()
            result3.curations[1].base.declaredLicenseMapping should beEmpty()
            result3.curations[2].base.declaredLicenseMapping.shouldContainExactly(
                mapOf("license c" to "CC-BY-1.0".toSpdx())
            )
        }
    }

    "isApplicable()" should {
        "accept an empty name and / or version" {
            val curation = PackageCuration(
                id = Identifier("Maven:com.android.tools"),

                // Hint: Curation data could set authors and a concluded license here to implement the concept of a
                // "trusted framework" (as [identified][id] by only the type and namespace) with
                // [ScannerConfiguration.skipConcluded] enabled.
                data = PackageCurationData()
            )

            assertSoftly {
                curation.isApplicable(Identifier("Maven:com.android.tools:common:25.3.0")) shouldBe true
                curation.isApplicable(Identifier("Maven:com.android.tools:common")) shouldBe true
                curation.isApplicable(Identifier("Maven:com.android.tools::25.3.0")) shouldBe true
                curation.isApplicable(Identifier("Maven:com.android.tools")) shouldBe true
            }
        }

        "not attempt to assert version ranges" {
            assertSoftly {
                packageCurationForVersion("2.0").isApplicable(identifierForVersion("2.0.1")) shouldBe false
                packageCurationForVersion("2.0.0").isApplicable(identifierForVersion("2.0.0.1")) shouldBe false
            }
        }

        "work for invalid semvers" {
            assertSoftly {
                packageCurationForVersion("3.0.3.jre11").isApplicable(identifierForVersion("3.0.3.jre11")) shouldBe true
                packageCurationForVersion("3.0.3.jre11").isApplicable(identifierForVersion("3.0.3.jre8")) shouldBe false
                packageCurationForVersion("1.2.3.4").isApplicable(identifierForVersion("1.2.3.9")) shouldBe false
            }
        }

        "not apply for invalid version ranges" {
            packageCurationForVersion("[2.0.0, 2.1.0]").isApplicable(identifierForVersion("2.0.0")) shouldBe false
        }

        "comply to the Ivy version matchers specifications" {
            assertSoftly {
                packageCurationForVersion("[1.0.0,2.0.0]").isApplicable(identifierForVersion("1.0.0")) shouldBe true
                packageCurationForVersion("[1.0.0,2.0.0]").isApplicable(identifierForVersion("1.23")) shouldBe true
                packageCurationForVersion("[1.0,2.0]").isApplicable(identifierForVersion("1.23")) shouldBe true
                packageCurationForVersion("1.+").isApplicable(identifierForVersion("1.23")) shouldBe true
                packageCurationForVersion("]1.0,)").isApplicable(identifierForVersion("1.0")) shouldBe false
            }
        }

        "apply three digit ranges to four digit versions" {
            assertSoftly {
                packageCurationForVersion("[1.0.0,2.0.0]").isApplicable(identifierForVersion("1.0.0.0")) shouldBe true
                packageCurationForVersion("[1.0.0,2.0.0]").isApplicable(identifierForVersion("1.2.3.4")) shouldBe true
                packageCurationForVersion("[1.0.0,2.0.0]").isApplicable(identifierForVersion("2.0.0.0")) shouldBe true

                packageCurationForVersion("[1.0.0,2.0.0]").isApplicable(identifierForVersion("0.9.0.0")) shouldBe false
                // TODO: This should not be applicable, but currently is due the usage of Semver.coerce() which coerces
                //       2.0.0.1 to 2.0.0.
                packageCurationForVersion("[1.0.0,2.0.0]").isApplicable(identifierForVersion("2.0.0.1")) shouldBe true
                packageCurationForVersion("[1.0.0,2.0.0]").isApplicable(identifierForVersion("2.0.1.0")) shouldBe false
            }
        }
    }
})

private fun packageCurationForVersion(version: String) =
    PackageCuration(identifierForVersion(version), PackageCurationData())

private fun identifierForVersion(version: String) = Identifier.EMPTY.copy(version = version)

private fun declaredLicenseMappingCuration(id: Identifier, vararg entries: Pair<String, String>): PackageCuration =
    PackageCuration(
        id,
        PackageCurationData(
            declaredLicenseMapping = entries.associate { it.first to it.second.toSpdx() }
        )
    )
