/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

package com.here.ort.model

import java.util.SortedSet

/**
 * Information about which scopes of the [Project] identified by [id] were scanned and ignored.
 */
data class ProjectScanScopes(
        /**
         * The [Identifier] of the [Project].
         */
        val id: Identifier,

        /**
         * The dependencies from these [Scope]s of the [Project] were scanned.
         */
        val scannedScopes: SortedSet<String>,

        /**
         * The dependencies from these [Scope]s of the [Project] were not scanned, except if they are also a dependency
         * of any of the [scannedScopes].
         */
        val ignoredScopes: SortedSet<String>
) : Comparable<ProjectScanScopes> {
    /**
     * A comparison function to sort project scan results by their identifier.
     */
    override fun compareTo(other: ProjectScanScopes) = id.compareTo(other.id)
}
