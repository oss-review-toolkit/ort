/*
 * Copyright (C) 2021 HERE Europe B.V.
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

package org.ossreviewtoolkit.model.utils

import com.opentable.db.postgres.embedded.EmbeddedPostgres

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import java.io.File
import java.time.Duration

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.test.createTestTempFile
import org.ossreviewtoolkit.utils.test.shouldNotBeNull

private val PG_STARTUP_WAIT = Duration.ofSeconds(20)

private val SOURCE_ARTIFACT_PROVENANCE = ArtifactProvenance(sourceArtifact = RemoteArtifact("url", Hash.create("hash")))
private val VCS_PROVENANCE = RepositoryProvenance(
    vcsInfo = VcsInfo(
        type = VcsType.GIT,
        url = "url",
        revision = "0000000000000000000000000000000000000000",
        resolvedRevision = "0000000000000000000000000000000000000000"
    )
)

private fun File.readTextAndDelete(): String {
    val text = readText()
    delete()

    return text
}

class PostgresFileArchiverStorageTest : WordSpec() {
    private lateinit var postgres: EmbeddedPostgres
    private lateinit var storage: PostgresFileArchiverStorage

    private fun createTempFile(content: String): File =
        createTestTempFile().apply {
            writeText(content)
        }

    override fun beforeSpec(spec: Spec) {
        postgres = EmbeddedPostgres.builder().setPGStartupWait(PG_STARTUP_WAIT).start()
        storage = PostgresFileArchiverStorage(postgres.postgresDatabase)
    }

    override fun afterSpec(spec: Spec) {
        postgres.close()
    }

    override fun isolationMode() = IsolationMode.InstancePerTest

    init {
        "hasArchive()" should {
            "return false when no archive for the given provenance has been added" {
                storage.hasArchive(VCS_PROVENANCE) shouldBe false
            }

            "return true when an archive for the given provenance has been added" {
                storage.addArchive(VCS_PROVENANCE, createTempFile("content"))

                storage.hasArchive(VCS_PROVENANCE) shouldBe true
            }
        }

        "getArchive()" should {
            "return the archives corresponding to the given provenance given such archive has been added" {
                storage.addArchive(VCS_PROVENANCE, createTempFile("VCS"))
                storage.addArchive(SOURCE_ARTIFACT_PROVENANCE, createTempFile("source artifact"))

                storage.getArchive(VCS_PROVENANCE) shouldNotBeNull { readTextAndDelete() shouldBe "VCS" }
                storage.getArchive(SOURCE_ARTIFACT_PROVENANCE) shouldNotBeNull {
                    readTextAndDelete() shouldBe "source artifact"
                }
            }
        }
    }
}
