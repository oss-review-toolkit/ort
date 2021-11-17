/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.scanner.experimental

import org.ossreviewtoolkit.model.RepositoryProvenance

/**
 * A storage for resolved [NestedProvenance]s.
 */
interface NestedProvenanceStorage {
    /**
     * Return the [NestedProvenanceResolutionResult] for the [root] provenance, or null if no result was stored.
     */
    fun readNestedProvenance(root: RepositoryProvenance): NestedProvenanceResolutionResult?

    /**
     * Put the resolution [result] for the [root] provenance into the storage. If the storage already contains an entry
     * for [root] it is overwritten.
     */
    fun putNestedProvenance(root: RepositoryProvenance, result: NestedProvenanceResolutionResult)
}

/**
 * The result of a [NestedProvenance] resolution.
 */
data class NestedProvenanceResolutionResult(
    /**
     * The resolved [NestedProvenance].
     */
    val nestedProvenance: NestedProvenance,

    /**
     * True if the revisions of all [RepositoryProvenance.vcsInfo] within [nestedProvenance] are fixed revisions.
     */
    val hasOnlyFixedRevisions: Boolean
)
