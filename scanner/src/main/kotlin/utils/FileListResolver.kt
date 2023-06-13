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

package org.ossreviewtoolkit.scanner.utils

import com.fasterxml.jackson.module.kotlin.readValue

import java.io.File

import org.ossreviewtoolkit.model.FileFormat
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.utils.ProvenanceFileStorage
import org.ossreviewtoolkit.model.writeValue
import org.ossreviewtoolkit.scanner.FileList
import org.ossreviewtoolkit.scanner.FileList.FileEntry
import org.ossreviewtoolkit.scanner.provenance.ProvenanceDownloader
import org.ossreviewtoolkit.utils.common.FileMatcher
import org.ossreviewtoolkit.utils.common.VCS_DIRECTORIES
import org.ossreviewtoolkit.utils.ort.createOrtTempFile

internal class FileListResolver(
    private val storage: ProvenanceFileStorage,
    private val provenanceDownloader: ProvenanceDownloader
) {
    fun resolve(provenance: KnownProvenance): FileList {
        storage.getFileList(provenance)?.let { return it }

        return provenanceDownloader.download(provenance).let { dir ->
            createFileList(dir).also { storage.putFileList(provenance, it) }
        }
    }

    fun has(provenance: KnownProvenance): Boolean = storage.hasFile(provenance)
}

private fun ProvenanceFileStorage.putFileList(provenance: KnownProvenance, fileList: FileList) {
    val tempFile = createOrtTempFile(prefix = "file-list", suffix = ".yml")

    tempFile.writeValue(fileList)
    putFile(provenance, tempFile)

    tempFile.delete()
}

private fun ProvenanceFileStorage.getFileList(provenance: KnownProvenance): FileList? {
    val file = getFile(provenance) ?: return null

    // Cannot rely on the extension of the file to reflect its type, so use YAML explicitly.
    return FileFormat.YAML.mapper.readValue<FileList>(file).also { file.delete() }
}

private val IGNORED_DIRECTORY_MATCHER by lazy {
    FileMatcher(patterns = VCS_DIRECTORIES.map { "**/$it" })
}

private fun createFileList(dir: File): FileList {
    val files = dir.walk().onEnter {
        !IGNORED_DIRECTORY_MATCHER.matches(it.relativeTo(dir).invariantSeparatorsPath)
    }.filter {
        it.isFile
    }.mapTo(mutableSetOf()) {
        FileEntry(path = it.relativeTo(dir).invariantSeparatorsPath, sha1 = HashAlgorithm.SHA1.calculate(it))
    }

    return FileList(ignorePatterns = IGNORED_DIRECTORY_MATCHER.patterns.toSet(), files = files)
}
