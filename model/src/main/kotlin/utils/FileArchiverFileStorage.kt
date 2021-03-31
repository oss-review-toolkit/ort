/*
 * Copyright (C) 2019-2021 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.ORT_NAME
import org.ossreviewtoolkit.utils.collectMessagesAsString
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.storage.FileStorage
import org.ossreviewtoolkit.utils.toHexString

/**
 * A [FileStorage] based storage for archive files.
 */
class FileArchiverFileStorage(
    /**
     * The [FileStorage] to use for storing the files.
     */
    private val storage: FileStorage
) : FileArchiverStorage {
    override fun hasArchive(provenance: KnownProvenance): Boolean {
        val archivePath = getArchivePath(provenance)

        return storage.exists(archivePath)
    }

    override fun addArchive(provenance: KnownProvenance, zipFile: File) {
        storage.write(getArchivePath(provenance), zipFile.inputStream())
    }

    override fun getArchive(provenance: KnownProvenance): File? {
        val archivePath = getArchivePath(provenance)

        val zipFile = kotlin.io.path.createTempFile(ORT_NAME, ".zip").toFile()

        return try {
            storage.read(archivePath).use { inputStream ->
                zipFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }

            zipFile
        } catch (e: IOException) {
            log.error { "Could not unarchive from $archivePath: ${e.collectMessagesAsString()}" }

            null
        }
    }
}

private val SHA1_DIGEST by lazy { MessageDigest.getInstance("SHA-1") }

/**
 * Calculate the SHA-1 hash of the storage key of this [KnownProvenance] instance.
 */
private fun KnownProvenance.hash(): String {
    val key = when (this) {
        is ArtifactProvenance -> "${sourceArtifact.url}${sourceArtifact.hash.value}"
        is RepositoryProvenance -> {
            // The content on the archives does not depend on the VCS path in general, thus that path must not be part
            // of the storage key. However, for Git-Repo that path must be part of the storage key because it denotes
            // the Git-Repo manifest location rather than the path to be (sparse) checked out.
            val path = vcsInfo.path.takeIf { vcsInfo.type == VcsType.GIT_REPO }.orEmpty()
            "${vcsInfo.type}${vcsInfo.url}${vcsInfo.resolvedRevision}$path"
        }
    }

    return SHA1_DIGEST.digest(key.toByteArray()).toHexString()
}

private fun getArchivePath(provenance: KnownProvenance): String = "${provenance.hash()}/archive.zip"
