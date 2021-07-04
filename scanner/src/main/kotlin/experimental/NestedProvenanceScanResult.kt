/*
 * Copyright (C) 2021 HERE Europe B.V.
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

import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.ScanResult

/**
 * A class that contains all [ScanResult]s for a [NestedProvenance].
 */
data class NestedProvenanceScanResult(
    /**
     * The [NestedProvenance] which the [scanResults] belong to.
     */
    val nestedProvenance: NestedProvenance,

    /**
     * A map of [KnownProvenance]s from [nestedProvenance] associated with lists of [ScanResult]s.
     */
    val scanResults: Map<KnownProvenance, List<ScanResult>>,
) {
    /**
     * Return a set of all [KnownProvenance]s contained in [nestedProvenance].
     */
    fun getProvenances(): Set<KnownProvenance> = nestedProvenance.getProvenances()

    /**
     * Return true if [scanResults] contains at least one scan result for each of the [KnownProvenance]s contained in
     * [nestedProvenance].
     */
    fun isComplete(): Boolean = getProvenances().all { scanResults[it]?.isNotEmpty() == true }
}
