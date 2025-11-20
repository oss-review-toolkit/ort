/*
 * Copyright (C) 2024 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.string.shouldNotStartWith
import io.kotest.matchers.string.shouldStartWith

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.UnknownProvenance
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType

class PurlExtensionsTest : WordSpec({
    "purl representations" should {
        "not suffix the scheme with '//'" {
            val purl = Identifier("type", "namespace", "name", "version").toPurl()

            purl shouldStartWith "pkg:"
            purl shouldNotStartWith "pkg://"
        }

        "not percent-encode the type" {
            val purl = Identifier("azAZ09.+-", "namespace", "name", "version").toPurl()

            purl shouldNotContain "%"
        }

        "ignore case in type" {
            val purl = Identifier("MaVeN", "namespace", "name", "version").toPurl()

            purl shouldBe purl.lowercase()
        }

        "use the generic type if it is not a known package manager" {
            val purl = Identifier("FooBar", "namespace", "name", "version").toPurl()

            purl shouldStartWith "pkg:generic"
        }

        "not use '/' for empty namespaces" {
            val purl = Identifier("generic", "", "name", "version").toPurl()

            purl shouldBe "pkg:generic/name@version"
        }

        "percent-encode namespace segments" {
            val purl = Identifier("generic", "name space/with spaces", "name", "version").toPurl()

            purl shouldBe "pkg:generic/name%20space/with%20spaces/name@version"
        }

        "percent-encode the name" {
            val purl = Identifier("generic", "namespace", "fancy name", "version").toPurl()

            purl shouldBe "pkg:generic/namespace/fancy%20name@version"
        }

        "percent-encode the version" {
            val purl = Identifier("generic", "namespace", "name", "release candidate").toPurl()

            purl shouldBe "pkg:generic/namespace/name@release%20candidate"
        }
    }

    "Provenance conversion" should {
        "work for extras of an artifact's provenance" {
            val provenance = ArtifactProvenance(
                sourceArtifact = RemoteArtifact(
                    url = "https://example.com/sources.zip",
                    hash = Hash(
                        value = "ddce269a1e3d054cae349621c198dd52",
                        algorithm = HashAlgorithm.MD5
                    )
                )
            )
            val id = Identifier("Maven:com.example:sources:1.2.3")

            val extras = provenance.toPurlExtras()
            val purl = id.toPurl(extras.qualifiers, extras.subpath)

            purl shouldBe "pkg:maven/com.example/sources@1.2.3?" +
                "checksum=md5:ddce269a1e3d054cae349621c198dd52&" +
                "download_url=https://example.com/sources.zip"
            purl.toProvenance() shouldBe provenance
        }

        "work for extras of a repository's provenance" {
            val provenance = RepositoryProvenance(
                vcsInfo = VcsInfo(
                    type = VcsType.GIT,
                    url = "https://github.com/apache/commons-text.git",
                    revision = "7643b12421100d29fd2b78053e77bcb04a251b2e",
                    path = "subpath"
                ),
                resolvedRevision = "7643b12421100d29fd2b78053e77bcb04a251b2e"
            )
            val id = Identifier("Maven:com.example:sources:1.2.3")

            val extras = provenance.toPurlExtras()
            val purl = id.toPurl(extras.qualifiers, extras.subpath)

            purl shouldBe "pkg:maven/com.example/sources@1.2.3?" +
                "resolved_revision=7643b12421100d29fd2b78053e77bcb04a251b2e&" +
                "vcs_revision=7643b12421100d29fd2b78053e77bcb04a251b2e&" +
                "vcs_type=Git&" +
                "vcs_url=https://github.com/apache/commons-text.git" +
                "#subpath"
            purl.toProvenance() shouldBe provenance
        }

        "work for a purl without qualifiers" {
            "pkg:npm/mime-db@1.33.0".toProvenance() shouldBe UnknownProvenance
        }
    }

    "When mapping to Identifier Purl representation" should {
        "fail to parse a non 'pkg' type purl" {
            shouldThrow<IllegalArgumentException> {
                "nonpkg:github/example.com/valid-with-namespace@1.0".toIdentifier()
            }
        }

        "fail to parse invalid purl format" {
            shouldThrow<IllegalArgumentException> {
                "github/example.com/valid-with-namespace@1.0".toIdentifier()
            }
        }

        "fail to parse a purl without type" {
            shouldThrow<IllegalArgumentException> {
                "pkg:EnterpriseLibrary.Common@6.0.1304".toIdentifier()
            }
        }

        "fail to parse a purl without type and version" {
            shouldThrow<IllegalArgumentException> {
                "pkg:EnterpriseLibrary.Common".toIdentifier()
            }
        }

        "fail to parse a purl containing forbidden characters in name" {
            shouldThrow<IllegalArgumentException> {
                "pkg:n&g?inx/nginx@0.8.9".toIdentifier()
            }
        }

        "fail to parse a purl containing forbidden characters in version" {
            shouldThrow<IllegalArgumentException> {
                "pkg:docker/cassandra@sha256:244fd47e07d1004f0aed9c".toIdentifier()
            }
        }

        "fail to parse a purl that name starts with number" {
            shouldThrow<IllegalArgumentException> {
                "pkg:3nginx/nginx@0.8.9".toIdentifier()
            }
        }

        "fail to parse a purl that contains forbidden characters in type" {
            shouldThrow<IllegalArgumentException> {
                "pkg:nginx:a/nginx@0.8.9".toIdentifier()
            }
        }

        "fail to parse a purl with invalid qualifier" {
            shouldThrow<IllegalArgumentException> {
                "pkg:npm/myartifact@1.0.0?in%20production=true".toIdentifier()
            }
        }

        "fail to parse a purl without name" {
            shouldThrow<IllegalArgumentException> {
                "pkg:maven/@1.3.4".toIdentifier()
            }
        }

        "fail to parse a purl without name and type" {
            shouldThrow<IllegalArgumentException> {
                "pkg:@6.0.1304".toIdentifier()
            }
        }

        "fail to parse a purl with percent-encoded colon" {
            shouldThrow<IllegalArgumentException> {
                "pkg%3Amaven/org.apache.commons/io".toIdentifier()
            }
        }

        "properly parse purls from purl spec examples" {
            "pkg:deb/debian/curl@7.50.3-1?arch=i386&distro=jessie"
                .toIdentifier() shouldBe Identifier("Deb", "debian", "curl", "7.50.3-1")

            "pkg:gem/jruby-launcher@1.1.2?platform=java"
                .toIdentifier() shouldBe Identifier("Gem", "", "jruby-launcher", "1.1.2")

            "pkg:golang/google.golang.org/genproto#googleapis/api/annotations"
                .toIdentifier() shouldBe Identifier("Go", "google.golang.org", "genproto", "")

            "pkg:maven/org.apache.xmlgraphics/batik-anim@1.9.1?repository_url=repo.spring.io%2Frelease"
                .toIdentifier() shouldBe Identifier("Maven", "org.apache.xmlgraphics", "batik-anim", "1.9.1")

            "pkg:npm/%40angular/animation@12.3.1"
                .toIdentifier() shouldBe Identifier("NPM", "@angular", "animation", "12.3.1")

            "pkg:nuget/EnterpriseLibrary.Common@6.0.1304"
                .toIdentifier() shouldBe Identifier("NuGet", "", "EnterpriseLibrary.Common", "6.0.1304")

            "pkg:pypi/django@1.11.1"
                .toIdentifier() shouldBe Identifier("PyPi", "", "django", "1.11.1")

            "pkg:rpm/fedora/curl@7.50.3-1.fc25?arch=i386&distro=fedora-25"
                .toIdentifier() shouldBe Identifier("RPM", "fedora", "curl", "7.50.3-1.fc25")

            "pkg:rpm/opensuse/curl@7.56.1-1.1.?arch=i386&distro=opensuse-tumbleweed"
                .toIdentifier() shouldBe Identifier("RPM", "opensuse", "curl", "7.56.1-1.1.")
        }
    }
})
