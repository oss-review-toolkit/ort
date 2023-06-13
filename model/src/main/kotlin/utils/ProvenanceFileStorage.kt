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

import java.io.File

import org.ossreviewtoolkit.model.KnownProvenance

/**
 * A generic storage interface that associates a [KnownProvenance] with a file.
 */
interface ProvenanceFileStorage {
    /**
     * Return whether a file is associated by [provenance].
     */
    fun hasFile(provenance: KnownProvenance): Boolean

    /**
     * Associate [provenance] with the given [file]. Overwrites any existing association by [provenance].
     */
    fun putFile(provenance: KnownProvenance, file: File)

    /**
     * Return the file associated by [provenance], or null if there is no such file. Note that the returned file is a
     * temporary file that the caller is responsible for.
     */
    fun getFile(provenance: KnownProvenance): File?
}
