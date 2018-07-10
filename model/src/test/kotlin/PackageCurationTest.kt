/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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

package com.here.ort.model

import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec

class PackageCurationTest : StringSpec() {
    init {
        "apply overwrites the correct values" {
            val pkg = Package(
                    id = Identifier(
                            provider = "Maven",
                            namespace = "org.hamcrest",
                            name = "hamcrest-core",
                            version = "1.3"
                    ),
                    declaredLicenses = sortedSetOf(),
                    description = "",
                    homepageUrl = "",
                    binaryArtifact = RemoteArtifact.EMPTY,
                    sourceArtifact = RemoteArtifact.EMPTY,
                    vcs = VcsInfo.EMPTY
            )
            val curation = PackageCuration(
                    id = pkg.id,
                    data = PackageCurationData(
                            declaredLicenses = sortedSetOf("license a", "license b"),
                            description = "description",
                            homepageUrl = "http://home.page",
                            binaryArtifact = RemoteArtifact(
                                    url = "http://binary.artifact",
                                    hash = "binary.hash",
                                    hashAlgorithm = HashAlgorithm.UNKNOWN
                            ),
                            sourceArtifact = RemoteArtifact(
                                    url = "http://source.artifact",
                                    hash = "source.hash",
                                    hashAlgorithm = HashAlgorithm.UNKNOWN
                            ),
                            vcs = VcsInfo(
                                    type = "git",
                                    url = "http://url.git",
                                    revision = "revision",
                                    resolvedRevision = "resolvedRevision",
                                    path = "path"
                            )
                    )
            )

            val curatedPkg = curation.apply(pkg.toCuratedPackage())

            curatedPkg.pkg.apply {
                id.toString() shouldBe pkg.id.toString()
                declaredLicenses shouldBe curation.data.declaredLicenses
                description shouldBe curation.data.description
                homepageUrl shouldBe curation.data.homepageUrl
                binaryArtifact shouldBe curation.data.binaryArtifact
                sourceArtifact shouldBe curation.data.sourceArtifact
                vcs shouldBe curation.data.vcs
            }

            curatedPkg.curations.size shouldBe 1
            curatedPkg.curations.first().base shouldBe pkg.diff(curatedPkg.pkg)
            curatedPkg.curations.first().curation shouldBe curation.data
        }

        "apply changes only curated fields" {
            val pkg = Package(
                    id = Identifier(
                            provider = "Maven",
                            namespace = "org.hamcrest",
                            name = "hamcrest-core",
                            version = "1.3"
                    ),
                    declaredLicenses = sortedSetOf("license a", "license b"),
                    description = "description",
                    homepageUrl = "homepageUrl",
                    binaryArtifact = RemoteArtifact.EMPTY,
                    sourceArtifact = RemoteArtifact.EMPTY,
                    vcs = VcsInfo(
                            type = "git",
                            url = "http://url.git",
                            revision = "revision",
                            resolvedRevision = "resolvedRevision",
                            path = "path"
                    )
            )
            val curation = PackageCuration(
                    id = pkg.id,
                    data = PackageCurationData(
                            homepageUrl = "http://home.page",
                            vcs = VcsInfo(
                                    type = "",
                                    url = "http://url.git",
                                    revision = ""
                            )
                    )
            )

            val curatedPkg = curation.apply(pkg.toCuratedPackage())

            curatedPkg.pkg.apply {
                id.toString() shouldBe pkg.id.toString()
                declaredLicenses shouldBe pkg.declaredLicenses
                description shouldBe pkg.description
                homepageUrl shouldBe curation.data.homepageUrl
                binaryArtifact shouldBe pkg.binaryArtifact
                sourceArtifact shouldBe pkg.sourceArtifact
                vcs shouldBe VcsInfo(
                        type = pkg.vcs.type,
                        url = curation.data.vcs!!.url,
                        revision = pkg.vcs.revision,
                        resolvedRevision = pkg.vcs.resolvedRevision,
                        path = pkg.vcs.path
                )
            }

            curatedPkg.curations.size shouldBe 1
            curatedPkg.curations.first().base shouldBe pkg.diff(curatedPkg.pkg)
            curatedPkg.curations.first().curation shouldBe curation.data
        }

        "applying curation fails when identifiers do not match" {
            val pkg = Package(
                    id = Identifier(
                            provider = "Maven",
                            namespace = "org.hamcrest",
                            name = "hamcrest-core",
                            version = "1.3"
                    ),
                    declaredLicenses = sortedSetOf(),
                    description = "",
                    homepageUrl = "",
                    binaryArtifact = RemoteArtifact.EMPTY,
                    sourceArtifact = RemoteArtifact.EMPTY,
                    vcs = VcsInfo.EMPTY
            )
            val curation = PackageCuration(
                    id = Identifier(
                            provider = "",
                            namespace = "",
                            name = "",
                            version = ""
                    ),
                    data = PackageCurationData(
                            homepageUrl = "http://home.page",
                            vcs = VcsInfo(
                                    type = "",
                                    url = "http://url.git",
                                    revision = ""
                            )
                    )
            )

            shouldThrow<IllegalArgumentException> {
                curation.apply(pkg.toCuratedPackage())
            }
        }

        "applying multiple curations in a row adds curation results to the curated package" {
            val id = Identifier("provider", "namespace", "name", "version")
            val pkg = Package.EMPTY.copy(id = id)
            val curation1 = PackageCuration(id, PackageCurationData(description = "description 1"))
            val curation2 = PackageCuration(id, PackageCurationData(description = "description 2"))
            val curation3 = PackageCuration(id, PackageCurationData(description = "description 3"))

            val result1 = curation1.apply(pkg.toCuratedPackage())
            val result2 = curation2.apply(result1)
            val result3 = curation3.apply(result2)

            result1.pkg.description shouldBe "description 1"
            result1.curations.size shouldBe 1
            result1.curations[0].base shouldBe PackageCurationData(description = "")
            result1.curations[0].curation shouldBe curation1.data

            result2.pkg.description shouldBe "description 2"
            result2.curations.size shouldBe 2
            result2.curations[0].base shouldBe PackageCurationData(description = "")
            result2.curations[0].curation shouldBe curation1.data
            result2.curations[1].base shouldBe PackageCurationData(description = "description 1")
            result2.curations[1].curation shouldBe curation2.data

            result3.pkg.description shouldBe "description 3"
            result3.curations.size shouldBe 3
            result3.curations[0].base shouldBe PackageCurationData(description = "")
            result3.curations[0].curation shouldBe curation1.data
            result3.curations[1].base shouldBe PackageCurationData(description = "description 1")
            result3.curations[1].curation shouldBe curation2.data
            result3.curations[2].base shouldBe PackageCurationData(description = "description 2")
            result3.curations[2].curation shouldBe curation3.data
        }
    }
}
