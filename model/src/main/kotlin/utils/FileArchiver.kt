/*
 * Copyright (C) 2019 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import kotlin.time.measureTime
import kotlin.time.measureTimedValue

import org.apache.logging.log4j.kotlin.logger
import org.apache.tika.Tika

import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.utils.common.FileMatcher
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.packZip
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.common.unpackZip
import org.ossreviewtoolkit.utils.ort.createOrtTempFile
import org.ossreviewtoolkit.utils.ort.ortDataDirectory
import org.ossreviewtoolkit.utils.ort.showStackTrace

/**
 * A class to archive files matched by provided patterns in a ZIP file that is stored in a [FileStorage][storage].
 */
class FileArchiver(
    /**
     * A collection of globs to match the paths of files that shall be archived. For details about the glob patterns see
     * [FileMatcher].
     */
    patterns: Collection<String>,

    /**
     * The [ProvenanceFileStorage] to use for archiving files.
     */
    private val storage: ProvenanceFileStorage
) {
    companion object {
        val DEFAULT_ARCHIVE_DIR by lazy { ortDataDirectory / "scanner" / "archive" }
    }

    private val matcher = FileMatcher(
        patterns = patterns,
        ignoreCase = true
    )

    /**
     * Return whether an archive corresponding to [provenance] exists.
     */
    fun hasArchive(provenance: KnownProvenance): Boolean = storage.hasData(provenance)

    /**
     * Archive the files matching [patterns] inside [rootDirectory] and put them to the [storage]. The archive is
     * associated with the given [provenance] and [id].
     */
    fun archive(rootDirectory: File, provenance: KnownProvenance, id: Identifier) {
        val subdirectory = getSubdirectoryForProvenance(provenance, id)
        val directories = setOf(rootDirectory, rootDirectory / subdirectory)

        logger.info { "Archiving files matching ${matcher.patterns} from '$rootDirectory'..." }

        val zipFile = createOrtTempFile(suffix = ".zip")
        val tika = Tika()

        val zipDuration = measureTime {
            rootDirectory.packZip(zipFile, overwrite = true) { file ->
                val matchingFile = directories.find {
                    val relativePath = file.relativeTo(it).invariantSeparatorsPath
                    matcher.matches(relativePath)
                } ?: return@packZip false

                if (!tika.detect(file).startsWith("text/")) {
                    logger.info { "Not adding file '$matchingFile' to archive because it is not a text file." }
                    return@packZip false
                }

                logger.debug { "Adding '$matchingFile' to archive." }
                true
            }
        }

        logger.info { "Archived directory '$rootDirectory' in $zipDuration." }

        val writeDuration = measureTime { storage.putData(provenance, zipFile.inputStream(), zipFile.length()) }

        logger.info { "Wrote archive of directory '$rootDirectory' to storage in $writeDuration." }

        zipFile.parentFile.safeDeleteRecursively()
    }

    /**
     * Unarchive the data for [provenance] to [directory]. Return true on success or false on failure.
     */
    fun unarchive(directory: File, provenance: KnownProvenance): Boolean {
        if (!storage.hasData(provenance)) {
            logger.info { "Could not find an archive for $provenance." }
            return false
        }

        val (zipInputStream, readDuration) = measureTimedValue { storage.getData(provenance) }

        logger.info { "Got archive for $provenance in $readDuration." }

        if (zipInputStream == null) return false

        return runCatching {
            val unzipDuration = measureTime { zipInputStream.unpackZip(directory) }

            logger.info { "Unarchived data for $provenance to '$directory' in $unzipDuration." }

            true
        }.onFailure {
            it.showStackTrace()

            logger.error { "Failed to unarchive data for $provenance: ${it.collectMessages()}" }
        }.isSuccess
    }
}

/**
 * Get the (potentially [id]-type specific) nested path directory for the given [provenance].
 */
fun getSubdirectoryForProvenance(provenance: KnownProvenance, id: Identifier): String =
    when (provenance) {
        // In case of a repository, match paths relative to the VCS path.
        is RepositoryProvenance -> provenance.vcsInfo.path

        // In case of a source artifact, match paths relative to the archive root or a type-specific directory.
        is ArtifactProvenance -> {
            // Java Archives (JARs) by convention (see e.g. the Apache Release Policy) often contain licensing
            // information as part of the "META-INF" directory.
            if (id.type == "Maven") "META-INF" else ""

            // TODO: Check if more types need special handling.
        }
    }
