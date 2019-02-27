/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package com.here.ort.utils

import ch.frankel.slf4k.*

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Stack

/**
 * A convenience function that stashes directories using a [DirectoryStash] instance.
 */
fun stashDirectories(vararg directories: File): Closeable = DirectoryStash(setOf(*directories))

/**
 * A Closable class which moves the given directories to temporary directories on initialization. On close, it deletes
 * any conflicting existing directories in the original location and then moves the original directories back.
 */
private class DirectoryStash(private val directories: Set<File>) : Closeable {
    private val stashedDirectories = mutableMapOf<File, File>()

    init {
        directories.forEach { originalDir ->
            // We need to check this on each iteration instead of filtering beforehand to properly handle parent / child
            // directories.
            if (originalDir.isDirectory) {
                // Create a temporary directory to move directories as-is into.
                val stashDir = createTempDir("ort", "stash", originalDir.parentFile)

                // Use a non-existing directory as the target to ensure the directory can be moved atomically.
                val tempDir = File(stashDir, originalDir.name)

                log.info {
                    "Temporarily moving directory from '${originalDir.absolutePath}' to '${tempDir.absolutePath}'."
                }

                Files.move(originalDir.toPath(), tempDir.toPath(), StandardCopyOption.ATOMIC_MOVE)

                stashedDirectories[originalDir] = tempDir
            }
        }
    }

    override fun close() {
        // Restore directories in reverse order of stashing to properly handle parent / child directories.
        directories.reversed().forEach { originalDir ->
            originalDir.safeDeleteRecursively()

            stashedDirectories[originalDir]?.let { tempDir ->
                log.info { "Moving back directory from '${tempDir.absolutePath}' to '${originalDir.absolutePath}'." }

                Files.move(tempDir.toPath(), originalDir.toPath(), StandardCopyOption.ATOMIC_MOVE)

                // Delete the top-level temporary directory which should be empty now.
                if (!tempDir.parentFile.delete()) {
                    throw IOException("Unable to delete the '${tempDir.parent}' directory.")
                }
            }
        }
    }
}
