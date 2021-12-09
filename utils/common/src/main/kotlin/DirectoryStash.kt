/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

import org.apache.logging.log4j.kotlin.Logging

/**
 * A convenience function that stashes directories using a [DirectoryStash] instance.
 */
fun stashDirectories(vararg directories: File): Closeable = DirectoryStash(setOf(*directories))

/**
 * A Closable class which temporarily moves away directories and moves them back on close. Any conflicting directory
 * created at the location of an original directory is deleted before the original state is restored. If a specified
 * directory did not exist on initialization, it will also not exist on close.
 */
private class DirectoryStash(directories: Set<File>) : Closeable {
    private companion object : Logging

    private val stashedDirectories: Map<File, File?> = directories.associateWith { originalDir ->
        // We need to check this on each iteration instead of filtering beforehand to properly handle parent / child
        // directories.
        if (originalDir.isDirectory) {
            // Create a temporary directory to move directories as-is into.
            val stashDir = createTempDirectory(originalDir.parentFile.toPath(), "stash").toFile()

            // Use a non-existing directory as the target to ensure the directory can be moved atomically.
            val tempDir = stashDir.resolve(originalDir.name)

            logger.info {
                "Temporarily moving directory from '${originalDir.absolutePath}' to '${tempDir.absolutePath}'."
            }

            originalDir.toPath().moveTo(tempDir.toPath(), StandardCopyOption.ATOMIC_MOVE)

            tempDir
        } else {
            null
        }
    }

    override fun close() {
        // Restore directories in reverse order of stashing to properly handle parent / child directories.
        stashedDirectories.keys.reversed().forEach { originalDir ->
            originalDir.safeDeleteRecursively()

            stashedDirectories[originalDir]?.let { tempDir ->
                logger.info { "Moving back directory from '${tempDir.absolutePath}' to '${originalDir.absolutePath}'." }

                tempDir.toPath().moveTo(originalDir.toPath(), StandardCopyOption.ATOMIC_MOVE)

                // Delete the top-level temporary directory which should be empty now.
                if (!tempDir.parentFile.delete()) {
                    throw IOException("Unable to delete the '${tempDir.parent}' directory.")
                }
            }
        }
    }
}
