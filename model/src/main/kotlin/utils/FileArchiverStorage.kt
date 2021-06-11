/*
 * Copyright (C) 2021 HERE Europe B.V.
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
 * A storage for file archives.
 */
interface FileArchiverStorage {
    /**
     * Return whether an archive corresponding to [provenance] exists.
     */
    fun hasArchive(provenance: KnownProvenance): Boolean

    /**
     * Add the [archive][zipFile] corresponding to [provenance]. Overwrites any existing archive corresponding to
     * [provenance].
     */
    fun addArchive(provenance: KnownProvenance, zipFile: File)

    /**
     * Return the archive corresponding to [provenance] or null if no such archive exists. The returned file is a
     * temporary file.
     */
    fun getArchive(provenance: KnownProvenance): File?
}
