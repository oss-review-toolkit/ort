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

/**
 * Bundles information about a remote artifact.
 */
data class RemoteArtifact(
    /**
     * The URL of the remote artifact.
     */
    val url: String,

    /**
     * The hash of the remote artifact.
     */
    val hash: Hash
) {
    companion object {
        /**
         * A constant for a [RemoteArtifact] where all properties are empty strings.
         */
        @JvmField
        val EMPTY = RemoteArtifact(
            url = "",
            hash = Hash.NONE
        )
    }
}

/**
 * Return this [RemoteArtifact] if not null or else [RemoteArtifact.EMPTY].
 */
fun RemoteArtifact?.orEmpty(): RemoteArtifact = this ?: RemoteArtifact.EMPTY
