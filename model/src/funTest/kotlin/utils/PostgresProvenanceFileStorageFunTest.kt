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

package org.ossreviewtoolkit.model.utils

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import java.io.ByteArrayInputStream
import java.io.InputStream

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.FileArchiverConfiguration
import org.ossreviewtoolkit.utils.test.PostgresListener

private val SOURCE_ARTIFACT_PROVENANCE = ArtifactProvenance(RemoteArtifact("https://example.com/", Hash.NONE))
private val VCS_PROVENANCE = RepositoryProvenance(
    vcsInfo = VcsInfo(
        type = VcsType.GIT,
        url = "url",
        revision = "0000000000000000000000000000000000000000"
    ),
    resolvedRevision = "0000000000000000000000000000000000000000"
)

class PostgresProvenanceFileStorageFunTest : WordSpec({
    val postgresListener = PostgresListener()
    lateinit var storage: PostgresProvenanceFileStorage

    extension(postgresListener)

    beforeEach {
        storage = PostgresProvenanceFileStorage(postgresListener.dataSource, FileArchiverConfiguration.TABLE_NAME)
    }

    "hasData()" should {
        "return false when no data for the given provenance has been added" {
            storage.hasData(VCS_PROVENANCE) shouldBe false
        }

        "return true when data for the given provenance has been added" {
            storage.putData(VCS_PROVENANCE, InputStream.nullInputStream(), 0L)

            storage.hasData(VCS_PROVENANCE) shouldBe true
        }
    }

    "putData()" should {
        "return the data corresponding to the given provenance given such data has been added" {
            val vcsByteArray = "VCS".toByteArray()
            val sourceArtifactByteArray = "source artifact".toByteArray()

            storage.putData(VCS_PROVENANCE, ByteArrayInputStream(vcsByteArray), vcsByteArray.size.toLong())
            storage.putData(
                SOURCE_ARTIFACT_PROVENANCE,
                ByteArrayInputStream(sourceArtifactByteArray),
                sourceArtifactByteArray.size.toLong()
            )

            storage.getData(VCS_PROVENANCE) shouldNotBeNull { String(use { readBytes() }) shouldBe "VCS" }
            storage.getData(SOURCE_ARTIFACT_PROVENANCE) shouldNotBeNull {
                String(use { readBytes() }) shouldBe "source artifact"
            }
        }

        "return the overwritten file corresponding to the given provenance" {
            val sourceArtifactByteArray = "source artifact".toByteArray()
            val sourceArtifactUpdatedByteArray = "source artifact updated".toByteArray()

            storage.putData(
                SOURCE_ARTIFACT_PROVENANCE,
                ByteArrayInputStream(sourceArtifactByteArray),
                sourceArtifactByteArray.size.toLong()
            )
            storage.putData(
                SOURCE_ARTIFACT_PROVENANCE,
                ByteArrayInputStream(sourceArtifactUpdatedByteArray),
                sourceArtifactUpdatedByteArray.size.toLong()
            )

            storage.getData(SOURCE_ARTIFACT_PROVENANCE) shouldNotBeNull {
                String(use { readBytes() }) shouldBe "source artifact updated"
            }
        }
    }
})
