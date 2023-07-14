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

import java.io.InputStream

import org.ossreviewtoolkit.model.KnownProvenance

/**
 * A generic storage interface that associates a [KnownProvenance] with a stream of data.
 */
interface ProvenanceFileStorage {
    /**
     * Return whether any data is associated by [provenance].
     */
    fun hasData(provenance: KnownProvenance): Boolean

    /**
     * Associate [provenance] with the given [data] of the provided [size]. Replaces any existing association by
     * [provenance]. The function implementation is responsible for closing the stream.
     */
    fun putData(provenance: KnownProvenance, data: InputStream, size: Long)

    /**
     * Return the data associated by [provenance], or null if there is no such data. Note that it is the responsibility
     * of the caller to close the stream.
     */
    fun getData(provenance: KnownProvenance): InputStream?
}
