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

package com.here.ort.utils.storage

import com.here.ort.utils.log
import com.here.ort.utils.safeMkdirs

import java.io.File
import java.io.InputStream

/**
 * A [FileStorage] that stores file in a [directory] of the local file system. The [read] and [write] operations are
 * [Synchronized].
 */
class LocalFileStorage(
    /**
     * The directory used to store files in.
     */
    val directory: File
) : FileStorage {
    init {
        if (!directory.exists()) {
            log.debug { "Creating directory '${directory.invariantSeparatorsPath}' for local file storage." }
            directory.safeMkdirs()
        } else {
            require(directory.isDirectory) {
                "Cannot use storage directory '${directory.invariantSeparatorsPath}' because it is not a directory."
            }
        }
    }

    @Synchronized
    override fun read(path: String): InputStream {
        val file = directory.resolve(path)

        require(file.canonicalFile.startsWith(directory.canonicalFile)) {
            "Path '$path' is not in directory '${directory.invariantSeparatorsPath}'."
        }

        return file.inputStream()
    }

    @Synchronized
    override fun write(path: String, inputStream: InputStream) {
        val file = directory.resolve(path)

        require(file.canonicalFile.startsWith(directory.canonicalFile)) {
            "Path '$path' is not in directory '${directory.invariantSeparatorsPath}'."
        }

        file.parentFile.safeMkdirs()

        file.outputStream().use {
            inputStream.copyTo(it)
        }
    }
}
