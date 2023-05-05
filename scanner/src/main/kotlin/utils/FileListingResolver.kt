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

import java.io.File

import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream

import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.fromYaml
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.model.utils.ProvenanceFileStorage
import org.ossreviewtoolkit.scanner.FileListing
import org.ossreviewtoolkit.scanner.FileListing.FileEntry
import org.ossreviewtoolkit.scanner.provenance.ProvenanceDownloader
import org.ossreviewtoolkit.utils.common.FileMatcher
import org.ossreviewtoolkit.utils.common.VCS_DIRECTORIES
import org.ossreviewtoolkit.utils.ort.createOrtTempFile

internal class FileListingResolver(
    private val storage: ProvenanceFileStorage,
    private val provenanceDownloader: ProvenanceDownloader
) {
    fun resolve(provenance: KnownProvenance): FileListing {
        storage.getFileListing(provenance)?.let { return it }

        return provenanceDownloader.download(provenance).let { dir ->
            createFileListing(dir).also { storage.putFileListing(provenance, it) }
        }
    }
}

private fun ProvenanceFileStorage.putFileListing(provenance: KnownProvenance, fileListing: FileListing) {
    val tempFile = createOrtTempFile(prefix = "file-listing", suffix = ".yml.xz")

    fileListing.toYaml().byteInputStream().use { input ->
        XZCompressorOutputStream(tempFile.outputStream()).use { output ->
            input.copyTo(output)
        }
    }

    putFile(provenance, tempFile)
    tempFile.delete()
}

private fun ProvenanceFileStorage.getFileListing(provenance: KnownProvenance): FileListing? {
    val file = getFile(provenance) ?: return null

    val yaml = XZCompressorInputStream(file.inputStream()).use { input ->
        input.bufferedReader().readText()
    }

    return yaml.fromYaml()
}

private val IGNORED_DIRECTORY_MATCHER by lazy {
    FileMatcher(patterns = VCS_DIRECTORIES.map { "**/$it" })
}

private fun createFileListing(dir: File): FileListing {
    val files = dir.walk().onEnter {
        !IGNORED_DIRECTORY_MATCHER.matches(it.relativeTo(dir).invariantSeparatorsPath)
    }.filter {
        it.isFile
    }.mapTo(mutableSetOf()) {
        FileEntry(path = it.relativeTo(dir).invariantSeparatorsPath, sha1 = HashAlgorithm.SHA1.calculate(it))
    }

    return FileListing(ignorePatterns = IGNORED_DIRECTORY_MATCHER.patterns.toSet(), files = files)
}
