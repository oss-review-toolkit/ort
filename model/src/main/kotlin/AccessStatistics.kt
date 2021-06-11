/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

import java.util.concurrent.atomic.AtomicInteger

/**
 * Statistics about reads and hits on a resource.
 */
data class AccessStatistics(
    /**
     * The number of read operations in the storage.
     */
    var numReads: AtomicInteger = AtomicInteger(0),

    /**
     * The number of read operations that returned an entry from the storage.
     */
    var numHits: AtomicInteger = AtomicInteger(0)
) {
    /**
     * Reset statistics to their initial state.
     */
    fun reset() {
        numReads.set(0)
        numHits.set(0)
    }
}
