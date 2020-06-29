/*
 * Copyright (C) 2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.utils.storage

import java.io.File
import java.io.IOException

import org.ossreviewtoolkit.utils.FileMatcher
import org.ossreviewtoolkit.utils.ORT_NAME
import org.ossreviewtoolkit.utils.collectMessagesAsString
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.packZip
import org.ossreviewtoolkit.utils.showStackTrace
import org.ossreviewtoolkit.utils.unpackZip

/**
 * A class to archive files matched by provided [patterns] in a ZIP file that is stored in a [FileStorage][storage].
 */
class FileArchiver(
    /**
     * A list of globs to match the paths of files that shall be archived. For details about the glob pattern see
     * [java.nio.file.FileSystem.getPathMatcher].
     */
    val patterns: List<String>,

    /**
     * The [FileStorage] to use for archiving files.
     */
    val storage: FileStorage
) {
    companion object {
        private const val ARCHIVE_FILE_NAME = "archive.zip"
    }

    private val matcher = FileMatcher(patterns)

    /**
     * Return whether '[storagePath]/[ARCHIVE_FILE_NAME]' exists.
     */
    fun hasArchive(storagePath: String) = storage.exists(getArchivePath(storagePath))

    /**
     * Archive all files in [directory] matching any of the configured [patterns] in the [storage]. The archived files
     * are zipped in the file '[storagePath]/[ARCHIVE_FILE_NAME]'.
     */
    fun archive(directory: File, storagePath: String) {
        val zipFile = createTempFile(ORT_NAME, ".zip")
        zipFile.deleteOnExit()

        directory.packZip(zipFile, overwrite = true) { path ->
            val relativePath = directory.toPath().relativize(path)
            matcher.matches(relativePath.toString()).also { result ->
                if (result) {
                    log.debug { "Adding '$relativePath' to archive." }
                } else {
                    log.debug { "Not adding '$relativePath' to archive." }
                }
            }
        }

        storage.write(getArchivePath(storagePath), zipFile.inputStream())
    }

    /**
     * Unarchive the file at '[storagePath]/[ARCHIVE_FILE_NAME]' to [directory].
     */
    fun unarchive(directory: File, storagePath: String): Boolean =
        try {
            storage.read(getArchivePath(storagePath)).use { input ->
                input.unpackZip(directory)
            }
            true
        } catch (e: IOException) {
            e.showStackTrace()

            log.error { "Could not unarchive from $storagePath: ${e.collectMessagesAsString()}" }

            false
        }

    private fun getArchivePath(storagePath: String) = "${storagePath.removeSuffix("/")}/$ARCHIVE_FILE_NAME"
}
