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

import java.io.InputStream

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.ort.storage.FileStorage

/**
 * A [FileStorage]-based implementation of [ProvenanceFileStorage] that stores files associated by [KnownProvenance]
 * in files.
 */
class FileProvenanceFileStorage(
    /**
     * The [FileStorage] to use for storing the files.
     */
    private val storage: FileStorage,

    /**
     * The name of the files to use for storing the contents of the associated files.
     */
    private val filename: String
) : ProvenanceFileStorage {
    init {
        require(filename.isNotEmpty()) {
            "The filename must not be empty."
        }
    }

    override fun hasData(provenance: KnownProvenance): Boolean {
        val filePath = getFilePath(provenance)

        return storage.exists(filePath)
    }

    override fun putData(provenance: KnownProvenance, data: InputStream, size: Long) {
        storage.write(getFilePath(provenance), data)
    }

    override fun getData(provenance: KnownProvenance): InputStream? {
        val filePath = getFilePath(provenance)

        return runCatching {
            storage.read(filePath)
        }.onFailure {
            logger.error { "Could not read from $filePath: ${it.collectMessages()}" }
        }.getOrNull()
    }

    private fun getFilePath(provenance: KnownProvenance): String = "${provenance.hash()}/$filename"
}

/**
 * Calculate the SHA-1 hash of the storage key of this [KnownProvenance] instance.
 */
private fun KnownProvenance.hash(): String {
    val key = when (this) {
        is ArtifactProvenance -> "${sourceArtifact.url}${sourceArtifact.hash.value}"
        is RepositoryProvenance -> "${vcsInfo.type}${vcsInfo.url}$resolvedRevision"
    }

    return HashAlgorithm.SHA1.calculate(key.toByteArray())
}
