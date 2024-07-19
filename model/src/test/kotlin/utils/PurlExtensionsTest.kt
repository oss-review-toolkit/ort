/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.UnknownProvenance
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType

class PurlExtensionsTest : StringSpec({
    "Artifact provenance can be converted to PURL extras and back" {
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
            "download_url=https%3A%2F%2Fexample.com%2Fsources.zip&" +
            "checksum=md5%3Addce269a1e3d054cae349621c198dd52"
        purl.toProvenance() shouldBe provenance
    }

    "Repository provenance can be converted to PURL extras and back" {
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
            "vcs_type=Git&" +
            "vcs_url=https%3A%2F%2Fgithub.com%2Fapache%2Fcommons-text.git&" +
            "vcs_revision=7643b12421100d29fd2b78053e77bcb04a251b2e&" +
            "resolved_revision=7643b12421100d29fd2b78053e77bcb04a251b2e" +
            "#subpath"
        purl.toProvenance() shouldBe provenance
    }

    "A clean PURL has unknown provenance" {
        "pkg:npm/mime-db@1.33.0".toProvenance() shouldBe UnknownProvenance
    }
})
