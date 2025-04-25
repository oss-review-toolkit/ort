/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import org.ossreviewtoolkit.utils.common.normalizeLineBreaks

class ProvenanceTest : WordSpec({
    "UnknownProvenance" should {
        val provenance = UnknownProvenance
        val json = provenance.toJson()

        "be serializable" {
            json shouldBe "{ }"
        }

        "be deserializable as Provenance" {
            json.fromJson<Provenance>() shouldBe UnknownProvenance
        }

        "be deserializable as UnknownProvenance" {
            json.fromJson<UnknownProvenance>() shouldBe UnknownProvenance
        }
    }

    "ArtifactProvenance" should {
        val provenance = ArtifactProvenance(
            sourceArtifact = RemoteArtifact(
                url = "url",
                hash = Hash("value", HashAlgorithm.UNKNOWN)
            )
        )

        val json = provenance.toJson()

        "be serializable" {
            json.normalizeLineBreaks() shouldBe """
                {
                  "source_artifact" : {
                    "url" : "url",
                    "hash" : {
                      "value" : "value",
                      "algorithm" : "UNKNOWN"
                    }
                  }
                }
            """.trimIndent()
        }

        "be deserializable as Provenance" {
            json.fromJson<Provenance>() shouldBe provenance
        }

        "be deserializable as KnownProvenance" {
            json.fromJson<KnownProvenance>() shouldBe provenance
        }

        "be deserializable as ArtifactProvenance" {
            json.fromJson<ArtifactProvenance>() shouldBe provenance
        }
    }

    "RepositoryProvenance" should {
        val provenance = RepositoryProvenance(
            vcsInfo = VcsInfo(
                type = VcsType.UNKNOWN,
                url = "url",
                revision = "revision",
                path = "path"
            ),
            resolvedRevision = "resolvedRevision"
        )

        val json = provenance.toJson()

        "be serializable" {
            json.normalizeLineBreaks() shouldBe """
                {
                  "vcs_info" : {
                    "type" : "",
                    "url" : "url",
                    "revision" : "revision",
                    "path" : "path"
                  },
                  "resolved_revision" : "resolvedRevision"
                }
            """.trimIndent()
        }

        "be serializable and deserializable as Provenance" {
            json.fromJson<Provenance>() shouldBe provenance
        }

        "be serializable and deserializable as KnownProvenance" {
            json.fromJson<KnownProvenance>() shouldBe provenance
        }

        "be serializable and deserializable as RepositoryProvenance" {
            json.fromJson<RepositoryProvenance>() shouldBe provenance
        }
    }
})
