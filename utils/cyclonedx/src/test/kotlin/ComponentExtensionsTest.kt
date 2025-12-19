/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.utils.cyclonedx

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

import org.cyclonedx.model.Component
import org.cyclonedx.model.ExternalReference
import org.cyclonedx.model.Hash
import org.cyclonedx.model.License
import org.cyclonedx.model.LicenseChoice
import org.cyclonedx.model.OrganizationalContact
import org.cyclonedx.model.OrganizationalEntity
import org.cyclonedx.model.license.Expression

import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType

class ComponentExtensionsTest : WordSpec({
    "toIdentifier()" should {
        "extract type from PURL" {
            val component = Component().apply {
                name = "cowboy"
                version = "2.9.0"
                purl = "pkg:hex/cowboy@2.9.0"
            }

            with(component.toIdentifier()) {
                type shouldBe "hex"
                name shouldBe "cowboy"
                version shouldBe "2.9.0"
            }
        }

        "use default type when PURL is missing" {
            val component = Component().apply {
                name = "cowboy"
                version = "2.9.0"
            }

            with(component.toIdentifier()) {
                type shouldBe "CycloneDX"
                name shouldBe "cowboy"
                version shouldBe "2.9.0"
            }
        }

        "use custom default type when provided" {
            val component = Component().apply {
                name = "cowboy"
                version = "2.9.0"
            }

            component.toIdentifier(defaultType = "CustomType").type shouldBe "CustomType"
        }

        "extract namespace from PURL" {
            val component = Component().apply {
                name = "ecto"
                version = "3.10.0"
                purl = "pkg:hex/elixir-ecto/ecto@3.10.0"
            }

            with(component.toIdentifier()) {
                namespace shouldBe "elixir-ecto"
                name shouldBe "ecto"
            }
        }

        "fall back to group when PURL has no namespace" {
            val component = Component().apply {
                name = "cowboy"
                version = "2.9.0"
                group = "ninenines"
                purl = "pkg:hex/cowboy@2.9.0"
            }

            component.toIdentifier().namespace shouldBe "ninenines"
        }

        "prefer PURL namespace over group" {
            val component = Component().apply {
                name = "ecto"
                version = "3.10.0"
                group = "other-group"
                purl = "pkg:hex/elixir-ecto/ecto@3.10.0"
            }

            component.toIdentifier().namespace shouldBe "elixir-ecto"
        }

        "handle unknown PURL types as generic" {
            val component = Component().apply {
                name = "custom"
                version = "1.0.0"
                purl = "pkg:unknown/custom@1.0.0"
            }

            component.toIdentifier().type shouldBe "generic"
        }
    }

    "toProject()" should {
        "create project with basic fields" {
            val component = Component().apply {
                name = "my_app"
                version = "1.0.0"
                purl = "pkg:hex/my_app@1.0.0"
                description = "My application"
            }

            with(component.toProject("mix.exs")) {
                id.type shouldBe "hex"
                id.name shouldBe "my_app"
                id.version shouldBe "1.0.0"
                definitionFilePath shouldBe "mix.exs"
                description shouldBe "My application"
            }
        }

        "extract VCS from external references" {
            val component = Component().apply {
                name = "my_app"
                version = "1.0.0"
                purl = "pkg:hex/my_app@1.0.0"
                externalReferences = listOf(
                    ExternalReference().apply {
                        type = ExternalReference.Type.VCS
                        url = "https://github.com/example/my_app"
                    }
                )
            }

            with(component.toProject("mix.exs").vcs) {
                type shouldBe VcsType.GIT
                url shouldBe "https://github.com/example/my_app.git"
            }
        }

        "extract homepage from external references" {
            val component = Component().apply {
                name = "my_app"
                version = "1.0.0"
                purl = "pkg:hex/my_app@1.0.0"
                externalReferences = listOf(
                    ExternalReference().apply {
                        type = ExternalReference.Type.WEBSITE
                        url = "https://example.com/my_app"
                    }
                )
            }

            component.toProject("mix.exs").homepageUrl shouldBe "https://example.com/my_app"
        }

        "extract authors and licenses" {
            val component = Component().apply {
                name = "my_app"
                version = "1.0.0"
                purl = "pkg:hex/my_app@1.0.0"
                authors = listOf(OrganizationalContact().apply { name = "John Doe" })
                licenses = LicenseChoice().apply {
                    licenses = listOf(
                        License().apply { id = "MIT" }
                    )
                }
            }

            with(component.toProject("mix.exs")) {
                authors shouldContainExactlyInAnyOrder listOf("John Doe")
                declaredLicenses shouldContainExactlyInAnyOrder listOf("MIT")
            }
        }

        "use custom defaultType when no PURL" {
            val component = Component().apply {
                name = "my_app"
                version = "1.0.0"
            }

            component.toProject("mix.exs", "Mix").id.type shouldBe "Mix"
        }

        "ignore defaultType when PURL is present" {
            val component = Component().apply {
                name = "my_app"
                version = "1.0.0"
                purl = "pkg:hex/my_app@1.0.0"
            }

            component.toProject("mix.exs", "Mix").id.type shouldBe "hex"
        }
    }

    "toPackage()" should {
        "create package with all fields" {
            val component = Component().apply {
                name = "cowboy"
                version = "2.9.0"
                purl = "pkg:hex/cowboy@2.9.0"
                description = "HTTP server"
                authors = listOf(OrganizationalContact().apply { name = "Loic Hoguin" })
                licenses = LicenseChoice().apply {
                    licenses = listOf(License().apply { id = "ISC" })
                }

                externalReferences = listOf(
                    ExternalReference().apply {
                        type = ExternalReference.Type.WEBSITE
                        url = "https://ninenines.eu"
                    },
                    ExternalReference().apply {
                        type = ExternalReference.Type.VCS
                        url = "https://github.com/ninenines/cowboy"
                    },
                    ExternalReference().apply {
                        type = ExternalReference.Type.DISTRIBUTION
                        url = "https://hex.pm/packages/cowboy/2.9.0"
                        hashes = listOf(
                            Hash(
                                Hash.Algorithm.SHA_256,
                                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
                            )
                        )
                    }
                )
            }

            with(component.toPackage()) {
                id.type shouldBe "hex"
                id.name shouldBe "cowboy"
                id.version shouldBe "2.9.0"
                purl shouldBe "pkg:hex/cowboy@2.9.0"
                description shouldBe "HTTP server"
                authors shouldContainExactlyInAnyOrder listOf("Loic Hoguin")
                declaredLicenses shouldContainExactlyInAnyOrder listOf("ISC")
                homepageUrl shouldBe "https://ninenines.eu"
                vcs.type shouldBe VcsType.GIT
                sourceArtifact.url shouldBe "https://hex.pm/packages/cowboy/2.9.0"
            }
        }

        "handle minimal component" {
            val component = Component().apply {
                name = "minimal"
                version = "1.0.0"
            }

            with(component.toPackage()) {
                id.name shouldBe "minimal"
                purl shouldBe ""
                description shouldBe ""
                authors.shouldBeEmpty()
                declaredLicenses.shouldBeEmpty()
                homepageUrl shouldBe ""
                vcs shouldBe VcsInfo.EMPTY
                sourceArtifact shouldBe RemoteArtifact.EMPTY
            }
        }

        "use custom defaultType when no PURL" {
            val component = Component().apply {
                name = "cowboy"
                version = "2.9.0"
            }

            component.toPackage("Rebar3").id.type shouldBe "Rebar3"
        }

        "ignore defaultType when PURL is present" {
            val component = Component().apply {
                name = "cowboy"
                version = "2.9.0"
                purl = "pkg:hex/cowboy@2.9.0"
            }

            component.toPackage("Rebar3").id.type shouldBe "hex"
        }
    }

    "extractLicenses()" should {
        "extract license by id" {
            val component = Component().apply {
                licenses = LicenseChoice().apply {
                    licenses = listOf(License().apply { id = "MIT" })
                }
            }

            component.extractLicenses() shouldContainExactlyInAnyOrder listOf("MIT")
        }

        "extract license by name when id is missing" {
            val component = Component().apply {
                licenses = LicenseChoice().apply {
                    licenses = listOf(License().apply { name = "Custom License" })
                }
            }

            component.extractLicenses() shouldContainExactlyInAnyOrder listOf("Custom License")
        }

        "prefer id over name" {
            val component = Component().apply {
                licenses = LicenseChoice().apply {
                    licenses = listOf(
                        License().apply {
                            id = "MIT"
                            name = "MIT License"
                        }
                    )
                }
            }

            component.extractLicenses() shouldContainExactlyInAnyOrder listOf("MIT")
        }

        "extract multiple licenses" {
            val component = Component().apply {
                licenses = LicenseChoice().apply {
                    licenses = listOf(
                        License().apply { id = "MIT" },
                        License().apply { id = "Apache-2.0" }
                    )
                }
            }

            component.extractLicenses() shouldContainExactlyInAnyOrder listOf("MIT", "Apache-2.0")
        }

        "return empty set when no licenses" {
            val component = Component()

            component.extractLicenses().shouldBeEmpty()
        }

        "extract individual licenses from SPDX expression" {
            val component = Component().apply {
                licenses = LicenseChoice().apply {
                    expression = Expression("Apache-2.0 AND (BSD-3-Clause OR LGPL-2.1-only)")
                }
            }

            component.extractLicenses() shouldContainExactlyInAnyOrder listOf(
                "Apache-2.0",
                "BSD-3-Clause",
                "LGPL-2.1-only"
            )
        }

        "extract single license from SPDX expression" {
            val component = Component().apply {
                licenses = LicenseChoice().apply {
                    expression = Expression("MIT")
                }
            }

            component.extractLicenses() shouldContainExactlyInAnyOrder listOf("MIT")
        }
    }

    "extractLicenseInfo()" should {
        "preserve SPDX expression in declaredLicensesProcessed" {
            val component = Component().apply {
                licenses = LicenseChoice().apply {
                    expression = Expression("Apache-2.0 AND (BSD-3-Clause OR LGPL-2.1-only)")
                }
            }

            val (declaredLicenses, declaredLicensesProcessed) = component.extractLicenseInfo()

            declaredLicenses shouldContainExactlyInAnyOrder listOf(
                "Apache-2.0",
                "BSD-3-Clause",
                "LGPL-2.1-only"
            )
            declaredLicensesProcessed.spdxExpression?.toString() shouldBe
                "Apache-2.0 AND (BSD-3-Clause OR LGPL-2.1-only)"
        }

        "process individual licenses with DeclaredLicenseProcessor" {
            val component = Component().apply {
                licenses = LicenseChoice().apply {
                    licenses = listOf(
                        License().apply { id = "MIT" },
                        License().apply { id = "Apache-2.0" }
                    )
                }
            }

            val (declaredLicenses, declaredLicensesProcessed) = component.extractLicenseInfo()

            declaredLicenses shouldContainExactlyInAnyOrder listOf("MIT", "Apache-2.0")
            // DeclaredLicenseProcessor combines licenses with AND; order may vary.
            declaredLicensesProcessed.spdxExpression?.decompose()?.map { it.toString() }?.toSet() shouldBe
                setOf("MIT", "Apache-2.0")
        }
    }

    "extractAuthors()" should {
        "prefer authors array over other fields" {
            val component = Component().apply {
                authors = listOf(
                    OrganizationalContact().apply { name = "Author One" },
                    OrganizationalContact().apply { name = "Author Two" }
                )
                manufacturer = OrganizationalEntity().apply { name = "Manufacturer" }
            }

            component.extractAuthors() shouldContainExactlyInAnyOrder listOf("Author One", "Author Two")
        }

        "fall back to manufacturer when authors array is empty" {
            val component = Component().apply {
                manufacturer = OrganizationalEntity().apply { name = "Manufacturer Inc" }
            }

            component.extractAuthors() shouldContainExactlyInAnyOrder listOf("Manufacturer Inc")
        }

        "use email when name is missing" {
            val component = Component().apply {
                authors = listOf(
                    OrganizationalContact().apply { email = "author@example.com" }
                )
            }

            component.extractAuthors() shouldContainExactlyInAnyOrder listOf("author@example.com")
        }

        "return empty set when no author information" {
            val component = Component()

            component.extractAuthors().shouldBeEmpty()
        }

        "skip blank names" {
            val component = Component().apply {
                authors = listOf(
                    OrganizationalContact().apply { name = "" },
                    OrganizationalContact().apply { name = "Valid Author" }
                )
            }

            component.extractAuthors() shouldContainExactlyInAnyOrder listOf("Valid Author")
        }
    }

    "extractVcs()" should {
        "extract VCS from external reference" {
            val component = Component().apply {
                externalReferences = listOf(
                    ExternalReference().apply {
                        type = ExternalReference.Type.VCS
                        url = "https://github.com/example/repo"
                    }
                )
            }

            with(component.extractVcs()) {
                type shouldBe VcsType.GIT
                url shouldBe "https://github.com/example/repo.git"
            }
        }

        "return empty VcsInfo when no VCS information" {
            val component = Component()

            component.extractVcs() shouldBe VcsInfo.EMPTY
        }
    }

    "extractSourceArtifact()" should {
        "extract from distribution reference" {
            val sha256Hash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
            val component = Component().apply {
                externalReferences = listOf(
                    ExternalReference().apply {
                        type = ExternalReference.Type.DISTRIBUTION
                        url = "https://hex.pm/packages/cowboy/2.9.0"
                        hashes = listOf(
                            Hash(Hash.Algorithm.SHA_256, sha256Hash)
                        )
                    }
                )
            }

            with(component.extractSourceArtifact()) {
                url shouldBe "https://hex.pm/packages/cowboy/2.9.0"
                hash.algorithm shouldBe HashAlgorithm.SHA256
                hash.value shouldBe sha256Hash
            }
        }

        "prefer stronger hash algorithms" {
            val md5Hash = "d41d8cd98f00b204e9800998ecf8427e"
            val sha256Hash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
            val sha512Hash = "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce" +
                "47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e"
            val component = Component().apply {
                externalReferences = listOf(
                    ExternalReference().apply {
                        type = ExternalReference.Type.DISTRIBUTION
                        url = "https://example.com/download"
                        hashes = listOf(
                            Hash(Hash.Algorithm.MD5, md5Hash),
                            Hash(Hash.Algorithm.SHA_256, sha256Hash),
                            Hash(Hash.Algorithm.SHA_512, sha512Hash)
                        )
                    }
                )
            }

            with(component.extractSourceArtifact().hash) {
                algorithm shouldBe HashAlgorithm.SHA512
                value shouldBe sha512Hash
            }
        }

        "return empty artifact when no distribution info" {
            val component = Component()

            component.extractSourceArtifact() shouldBe RemoteArtifact.EMPTY
        }

        "handle distribution with URL but no hash" {
            val component = Component().apply {
                externalReferences = listOf(
                    ExternalReference().apply {
                        type = ExternalReference.Type.DISTRIBUTION
                        url = "https://example.com/download"
                    }
                )
            }

            with(component.extractSourceArtifact()) {
                url shouldBe "https://example.com/download"
                hash shouldBe org.ossreviewtoolkit.model.Hash.NONE
            }
        }
    }

    "findExternalReferenceUrl()" should {
        "find URL by type" {
            val component = Component().apply {
                externalReferences = listOf(
                    ExternalReference().apply {
                        type = ExternalReference.Type.WEBSITE
                        url = "https://example.com"
                    }
                )
            }

            component.findExternalReferenceUrl(ExternalReference.Type.WEBSITE) shouldBe "https://example.com"
        }

        "return empty string when type not found" {
            val component = Component().apply {
                externalReferences = listOf(
                    ExternalReference().apply {
                        type = ExternalReference.Type.WEBSITE
                        url = "https://example.com"
                    }
                )
            }

            component.findExternalReferenceUrl(ExternalReference.Type.VCS) shouldBe ""
        }

        "skip blank URLs" {
            val component = Component().apply {
                externalReferences = listOf(
                    ExternalReference().apply {
                        type = ExternalReference.Type.WEBSITE
                        url = ""
                    },
                    ExternalReference().apply {
                        type = ExternalReference.Type.WEBSITE
                        url = "https://example.com"
                    }
                )
            }

            component.findExternalReferenceUrl(ExternalReference.Type.WEBSITE) shouldBe "https://example.com"
        }
    }
})
