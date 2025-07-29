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

@file:Suppress("Filename")

package org.ossreviewtoolkit.utils.common

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.file.StandardCopyOption

import kotlin.io.path.createTempDirectory
import kotlin.io.path.moveTo

import org.apache.logging.log4j.kotlin.logger

/**
 * A convenience function that stashes directories using a [DirectoryStash] instance.
 */
fun stashDirectories(vararg directories: File): Closeable = DirectoryStash(setOf(*directories))

/**
 * A [Closeable] class which temporarily moves away directories and moves them back on close. Any conflicting directory
 * created at the location of an original directory is deleted before the original state is restored. If a specified
 * directory did not exist on initialization, it will also not exist on close.
 */
class DirectoryStash(directories: Set<File>) : Closeable {
    private val stashedDirectories: Map<File, File?> = directories.associateWith { originalDir ->
        // Check this on each iteration instead of filtering beforehand to properly handle parent / child directories.
        if (originalDir.isDirectory) {
            // Create a temporary directory to move the original directory into as a sibling of the original directory
            // to ensure it resides on the same file system for being able to perform an atomic move.
            val tempDir = createTempDirectory(originalDir.parentFile.toPath(), ".stash").toFile()

            // Use a non-existing directory as the target to ensure the directory can be moved atomically.
            val stashDir = tempDir / originalDir.name

            logger.info {
                "Temporarily moving directory from '${originalDir.absolutePath}' to '${stashDir.absolutePath}'."
            }

            originalDir.toPath().moveTo(stashDir.toPath(), StandardCopyOption.ATOMIC_MOVE)

            stashDir
        } else {
            null
        }
    }

    override fun close() {
        // Restore directories in reverse order of stashing to properly handle parent / child directories.
        stashedDirectories.keys.reversed().forEach { originalDir ->
            originalDir.safeDeleteRecursively()

            stashedDirectories[originalDir]?.let { stashDir ->
                logger.info {
                    "Moving back directory from '${stashDir.absolutePath}' to '${originalDir.absolutePath}'."
                }

                stashDir.toPath().moveTo(originalDir.toPath(), StandardCopyOption.ATOMIC_MOVE)

                // Delete the top-level temporary directory which should be empty now.
                if (!stashDir.parentFile.delete()) {
                    throw IOException("Unable to delete the '${stashDir.parent}' directory.")
                }
            }
        }
    }
}
