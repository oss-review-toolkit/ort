/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.utils.common

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.file.StandardCopyOption

import kotlin.io.path.createTempDirectory
import kotlin.io.path.moveTo

import org.apache.logging.log4j.kotlin.logger

typealias FileStash = DirectoryStash

/**
 * A [Closeable] class which temporarily moves away directories / [files] and moves them back on close. Any conflicting
 * directory / file created at the location of an original directory / file is deleted before the original state is
 * restored. If a specified directory / file did not exist on initialization, it will also not exist on close.
 */
class DirectoryStash(files: Set<File>) : Closeable {
    private val stash: Map<File, File?> = files.associateWith { original ->
        // Check this on each iteration instead of filtering beforehand to properly handle parent / child directories.
        if (!original.exists()) return@associateWith null

        // Create a temporary directory to move the original directory / file into as a sibling of the original
        // directory / file to ensure it resides on the same file system for being able to perform an atomic move.
        val tempDir = createTempDirectory(original.parentFile.toPath(), ".stash").toFile()

        val stashDir = tempDir / original.name

        logger.info {
            val thing = if (original.isDirectory) "directory" else "file"
            "Temporarily moving $thing from '${original.absolutePath}' to '${stashDir.absolutePath}'."
        }

        original.toPath().moveTo(stashDir.toPath(), StandardCopyOption.ATOMIC_MOVE)

        stashDir
    }

    override fun close() {
        // Restore directories / files in reverse order of stashing to properly handle parent / child directories.
        stash.keys.reversed().forEach { original ->
            original.safeDeleteRecursively()

            stash[original]?.let { stashDir ->
                stashDir.toPath().moveTo(original.toPath(), StandardCopyOption.ATOMIC_MOVE)

                logger.info {
                    val thing = if (original.isDirectory) "directory" else "file"
                    "Moved back $thing from '${stashDir.absolutePath}' to '${original.absolutePath}'."
                }

                // Delete the top-level temporary directory which should be empty now.
                if (!stashDir.parentFile.delete()) {
                    throw IOException("Unable to delete the '${stashDir.parent}' directory.")
                }
            }
        }
    }
}

/**
 * A convenience function that stashes directories / files using a [DirectoryStash] instance.
 */
fun stashDirectories(vararg files: File): Closeable = DirectoryStash(setOf(*files))

/**
 * A convenience function that stashes directories / files using a [DirectoryStash] instance.
 */
fun stashFiles(vararg files: File): Closeable = FileStash(setOf(*files))
