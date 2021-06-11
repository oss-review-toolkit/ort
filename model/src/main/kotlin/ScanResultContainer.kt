/*
 * Copyright (C) 2017-2021 HERE Europe B.V.
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

/**
 * A container for [ScanResult]s for the package identified by [id].
 */
data class ScanResultContainer(
    /**
     * The [Identifier] of the package these [results] belong to.
     */
    val id: Identifier,

    /**
     * The list of [ScanResult]s from potentially multiple scanners and / or with different package provenance.
     */
    val results: List<ScanResult>
) : Comparable<ScanResultContainer> {
    /**
     * A comparison function to sort scan result containers by their identifier.
     */
    override fun compareTo(other: ScanResultContainer) = id.compareTo(other.id)
}
