/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import com.fasterxml.jackson.databind.annotation.JsonSerialize

import org.ossreviewtoolkit.model.utils.FileListEntrySortedSetConverter
import org.ossreviewtoolkit.utils.common.getDuplicates

/**
 * The file info for files contained in [provenance].
 */
data class FileList(
    /**
     * The provenance this file list corresponds to.
     */
    val provenance: KnownProvenance,

    /**
     * The files contained in [provenance], excluding directories which are certainly irrelevant, like e.g. the `.git`
     * directory.
     */
    @JsonSerialize(converter = FileListEntrySortedSetConverter::class)
    val files: Set<Entry>
) {
    data class Entry(
        /**
         * The path of the file relative to the root of the provenance corresponding to the enclosing [FileList].
         */
        val path: String,

        /**
         * The sha1 checksum of the file, consisting of 40 lowercase hexadecimal digits.
         */
        val sha1: String
    ) {
        init {
            require(path.isNotBlank()) { "The path must not be blank." }
            require(sha1.isNotBlank()) { "The sha1 must not be blank." }
        }
    }

    init {
        files.getDuplicates { it.path }.keys.let { duplicateFilePaths ->
            require(duplicateFilePaths.isEmpty()) {
                "Found duplicate file paths which is not allowed: ${duplicateFilePaths.joinToString()}."
            }
        }
    }

    /**
     * Merge this [FileList] with the given [other] [FileList].
     *
     * Both [FileList]s must have the same [provenance], otherwise an [IllegalArgumentException] is thrown.
     */
    operator fun plus(other: FileList) =
        FileList(
            provenance = provenance.also {
                require(it == other.provenance) {
                    "Cannot merge FileLists with different provenance: $it != ${other.provenance}."
                }
            },
            files = files + other.files
        )
}
