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

package org.ossreviewtoolkit.scanner

import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.util.StdConverter

import java.util.SortedSet

import org.ossreviewtoolkit.scanner.FileList.FileEntry
import org.ossreviewtoolkit.utils.common.getDuplicates
import org.ossreviewtoolkit.utils.ort.StringSortedSetConverter

/**
 * The model to store a file list for a resolved provenance.
 */
data class FileList(
    /**
     * The set of glob expressions which have been used to match directories to be excluded from the file list.
     */
    @JsonSerialize(converter = StringSortedSetConverter::class)
    val ignorePatterns: Set<String>,

    /**
     * The set of files contained in the resolved provenance, excluding files which are within a directory ignored by
     * [ignorePatterns].
     */
    @JsonSerialize(converter = FileEntrySortedSetConverter::class)
    val files: Set<FileEntry>
) {
    data class FileEntry(
        val path: String,
        val sha1: String
    )

    init {
        val duplicates = files.getDuplicates { it.path }.keys

        require(duplicates.isEmpty()) {
            "The file list contains duplicate paths which is not allowed: ${duplicates.joinToString()}."
        }
    }
}

private class FileEntrySortedSetConverter : StdConverter<Set<FileEntry>, SortedSet<FileEntry>>() {
    override fun convert(value: Set<FileEntry>) = value.toSortedSet(compareBy { it.path })
}
