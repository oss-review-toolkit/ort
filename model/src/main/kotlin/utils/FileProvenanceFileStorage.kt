/*
 * Copyright (C) 2019 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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
import java.io.IOException
import java.security.MessageDigest

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.encodeHex
import org.ossreviewtoolkit.utils.ort.createOrtTempFile
import org.ossreviewtoolkit.utils.ort.storage.FileStorage

/**
 * A [FileStorage] based implementation of [ProvenanceFileStorage].
 */
class FileProvenanceFileStorage(
    /**
     * The [FileStorage] to use for storing the files.
     */
    private val storage: FileStorage,

    /**
     * The filename of the files.
     */
    private val filename: String,
) : ProvenanceFileStorage {
    private companion object : Logging

    init {
        require(filename.isNotEmpty()) {
            "The filename must not be empty."
        }
    }

    override fun hasFile(provenance: KnownProvenance): Boolean {
        val filePath = getFilePath(provenance)

        return storage.exists(filePath)
    }

    override fun addFile(provenance: KnownProvenance, file: File) {
        storage.write(getFilePath(provenance), file.inputStream())
    }

    override fun getFile(provenance: KnownProvenance): File? {
        val filePath = getFilePath(provenance)

        val file = createOrtTempFile(suffix = File(filename).extension)

        return try {
            storage.read(filePath).use { inputStream ->
                file.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            file
        } catch (e: IOException) {
            logger.error { "Could not read from $filePath: ${e.collectMessages()}" }

            null
        }
    }

    private fun getFilePath(provenance: KnownProvenance): String = "${provenance.hash()}/$filename"
}

private val SHA1_DIGEST by lazy { MessageDigest.getInstance("SHA-1") }

/**
 * Calculate the SHA-1 hash of the storage key of this [KnownProvenance] instance.
 */
private fun KnownProvenance.hash(): String {
    val key = when (this) {
        is ArtifactProvenance -> "${sourceArtifact.url}${sourceArtifact.hash.value}"
        is RepositoryProvenance -> "${vcsInfo.type}${vcsInfo.url}$resolvedRevision"
    }

    return SHA1_DIGEST.digest(key.toByteArray()).encodeHex()
}
