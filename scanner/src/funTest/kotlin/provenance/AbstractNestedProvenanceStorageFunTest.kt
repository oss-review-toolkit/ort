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

package org.ossreviewtoolkit.scanner.provenance

import io.kotest.core.listeners.TestListener
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType

abstract class AbstractNestedProvenanceStorageFunTest(vararg listeners: TestListener) : WordSpec() {
    private lateinit var storage: NestedProvenanceStorage

    protected abstract fun createStorage(): NestedProvenanceStorage

    init {
        extensions(listeners.asList())

        beforeEach {
            storage = createStorage()
        }

        "Adding a result" should {
            "succeed" {
                val root = createRepositoryProvenance()
                val result = NestedProvenanceResolutionResult(createNestedProvenance(root), true)

                storage.writeNestedProvenance(root, result)

                storage.readNestedProvenance(root) shouldBe result
            }

            "overwrite a previously stored result" {
                val root = createRepositoryProvenance()
                val result1 = NestedProvenanceResolutionResult(
                    createNestedProvenance(root, mapOf("path" to root)), true
                )
                val result2 = NestedProvenanceResolutionResult(createNestedProvenance(root), true)

                storage.writeNestedProvenance(root, result1)
                storage.writeNestedProvenance(root, result2)

                storage.readNestedProvenance(root) shouldBe result2
            }
        }
    }
}

private fun createVcsInfo(
    type: VcsType = VcsType.GIT,
    url: String = "https://github.com/apache/logging-log4j2.git",
    revision: String = "be881e503e14b267fb8a8f94b6d15eddba7ed8c4"
) = VcsInfo(type, url, revision)

private fun createRepositoryProvenance(
    vcsInfo: VcsInfo = createVcsInfo(),
    resolvedRevision: String = vcsInfo.revision
) = RepositoryProvenance(vcsInfo, resolvedRevision)

private fun createNestedProvenance(
    root: KnownProvenance,
    subRepositories: Map<String, RepositoryProvenance> = emptyMap()
) = NestedProvenance(root, subRepositories)
