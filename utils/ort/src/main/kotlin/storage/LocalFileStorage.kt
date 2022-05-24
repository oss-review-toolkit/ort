/*
 * Copyright (C) 2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.utils.ort.storage

import java.io.File
import java.io.InputStream
import java.io.OutputStream

import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.ort.log

/**
 * A [FileStorage] that stores files in a [directory] of the local file system. The [read] and [write] operations are
 * [Synchronized].
 */
open class LocalFileStorage(
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

    /**
     * Return the internally used path, which might differ from the provided [path] e.g. in case a suffix is added to
     * denote a compression scheme.
     */
    open fun transformPath(path: String): String = path

    override fun exists(path: String) = directory.resolve(path).exists()

    @Synchronized
    override fun read(path: String): InputStream {
        val file = directory.resolve(path)

        require(file.canonicalFile.startsWith(directory.canonicalFile)) {
            "Path '$path' is not in directory '${directory.invariantSeparatorsPath}'."
        }

        return file.inputStream()
    }

    /**
     * Return the output stream to be used when writing to the provided [path].
     */
    protected open fun getOutputStream(path: String): OutputStream {
        val file = directory.resolve(path)

        require(file.canonicalFile.startsWith(directory.canonicalFile)) {
            "Path '$path' is not in directory '${directory.invariantSeparatorsPath}'."
        }

        file.parentFile.safeMkdirs()

        return file.outputStream()
    }

    @Synchronized
    override fun write(path: String, inputStream: InputStream) {
        getOutputStream(path).use {
            inputStream.copyTo(it)
        }
    }
}
