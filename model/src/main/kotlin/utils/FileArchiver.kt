/*
 * Copyright (C) 2019-2021 HERE Europe B.V.
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

import kotlin.time.measureTime
import kotlin.time.measureTimedValue

import org.ossreviewtoolkit.model.KnownProvenance
import org.ossreviewtoolkit.utils.common.FileMatcher
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.packZip
import org.ossreviewtoolkit.utils.common.unpackZip
import org.ossreviewtoolkit.utils.ort.createOrtTempFile
import org.ossreviewtoolkit.utils.ort.log
import org.ossreviewtoolkit.utils.ort.ortDataDirectory
import org.ossreviewtoolkit.utils.ort.showStackTrace
import org.ossreviewtoolkit.utils.ort.storage.FileStorage

/**
 * A class to archive files matched by provided patterns in a ZIP file that is stored in a [FileStorage][storage].
 */
class FileArchiver(
    /**
     * A collection of globs to match the paths of files that shall be archived. For details about the glob pattern see
     * [java.nio.file.FileSystem.getPathMatcher].
     */
    patterns: Collection<String>,

    /**
     * The [FileArchiverStorage] to use for archiving files.
     */
    internal val storage: FileArchiverStorage
) {
    constructor(
        /**
         * A collection of globs to match the paths of files that shall be archived. For details about the glob pattern
         * see [java.nio.file.FileSystem.getPathMatcher].
         */
        patterns: Collection<String>,

        /**
         * The [FileStorage] to use for archiving files.
         */
        storage: FileStorage
    ) : this(patterns, FileArchiverFileStorage(storage))

    companion object {
        val DEFAULT_ARCHIVE_DIR by lazy { ortDataDirectory.resolve("scanner/archive") }
    }

    private val matcher = FileMatcher(
        patterns = patterns,
        ignoreCase = true
    )

    /**
     * Return whether an archive corresponding to [provenance] exists.
     */
    fun hasArchive(provenance: KnownProvenance): Boolean = storage.hasArchive(provenance)

    /**
     * Archive all files in [directory] matching any of the configured patterns in the [storage].
     */
    fun archive(directory: File, provenance: KnownProvenance) {
        log.info { "Archiving files matching ${matcher.patterns} from '$directory'..." }

        val zipFile = createOrtTempFile(suffix = ".zip")

        val zipDuration = measureTime {
            directory.packZip(zipFile, overwrite = true) { file ->
                val relativePath = file.relativeTo(directory).invariantSeparatorsPath

                matcher.matches(relativePath).also { result ->
                    log.debug {
                        if (result) {
                            "Adding '$relativePath' to archive."
                        } else {
                            "Not adding '$relativePath' to archive."
                        }
                    }
                }
            }
        }

        log.info { "Archived directory '$directory' in $zipDuration." }

        val writeDuration = measureTime { storage.addArchive(provenance, zipFile) }

        log.info { "Wrote archive of directory '$directory' to storage in $writeDuration." }

        zipFile.delete()
    }

    /**
     * Unarchive the archive corresponding to [provenance].
     */
    fun unarchive(directory: File, provenance: KnownProvenance): Boolean {
        val (zipFile, readDuration) = measureTimedValue { storage.getArchive(provenance) }

        log.info { "Read archive of directory '$directory' from storage in $readDuration." }

        if (zipFile == null) return false

        return try {
            val unzipDuration = measureTime { zipFile.unpackZip(directory) }

            log.info { "Unarchived directory '$directory' in $unzipDuration." }

            true
        } catch (e: IOException) {
            e.showStackTrace()

            log.error { "Could not extract ${zipFile.absolutePath}: ${e.collectMessages()}" }

            false
        } finally {
            zipFile.delete()
        }
    }
}
