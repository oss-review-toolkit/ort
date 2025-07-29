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

import kotlin.time.measureTime
import kotlin.time.measureTimedValue

import org.apache.logging.log4j.kotlin.logger
import org.apache.tika.Tika
import org.apache.tika.mime.MimeTypes

import org.ossreviewtoolkit.model.KnownProvenance
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
     * Archive all files in [directory] matching any of the configured patterns in the [storage].
     */
    fun archive(directory: File, provenance: KnownProvenance) {
        logger.info { "Archiving files matching ${matcher.patterns} from '$directory'..." }

        val zipFile = createOrtTempFile(suffix = ".zip")
        val tika = Tika()

        val zipDuration = measureTime {
            directory.packZip(zipFile, overwrite = true) { file ->
                val relativePath = file.relativeTo(directory).invariantSeparatorsPath

                if (!matcher.matches(relativePath)) {
                    logger.debug {
                        "Not adding '$relativePath' to archive because it does not match the configured patterns."
                    }

                    return@packZip false
                }

                if (tika.detect(file) != MimeTypes.PLAIN_TEXT) {
                    logger.info { "Not adding file '$relativePath' to archive because it is not a text file." }
                    return@packZip false
                }

                logger.debug { "Adding '$relativePath' to archive." }
                true
            }
        }

        logger.info { "Archived directory '$directory' in $zipDuration." }

        val writeDuration = measureTime { storage.putData(provenance, zipFile.inputStream(), zipFile.length()) }

        logger.info { "Wrote archive of directory '$directory' to storage in $writeDuration." }

        zipFile.parentFile.safeDeleteRecursively()
    }

    /**
     * Unarchive the archive corresponding to [provenance].
     */
    fun unarchive(directory: File, provenance: KnownProvenance): Boolean {
        if (!storage.hasData(provenance)) {
            logger.info { "Could not find archive of directory '$directory'." }
            return false
        }

        val (zipInputStream, readDuration) = measureTimedValue { storage.getData(provenance) }

        logger.info { "Read archive of directory '$directory' from storage in $readDuration." }

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
