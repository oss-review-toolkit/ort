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

package org.ossreviewtoolkit.utils.ort.storage

import java.io.File
import java.io.InputStream
import java.io.OutputStream

import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.safeMkdirs

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
    /**
     * Return the internally used path, which might differ from the provided [path] e.g. in case a suffix is added to
     * denote a compression scheme.
     */
    open fun transformPath(path: String): String = path

    override fun exists(path: String) = directory.resolve(transformPath(path)).exists()

    @Synchronized
    override fun read(path: String): InputStream {
        val file = directory / transformPath(path)

        require(file.canonicalFile.startsWith(directory.canonicalFile)) {
            "Path '$path' is not in directory '${directory.invariantSeparatorsPath}'."
        }

        return file.inputStream()
    }

    /**
     * Ensure that [path] resolves to a file in [directory], create any parent directories if needed, and return an
     * output stream for writing to the file.
     */
    protected open fun safeOutputStream(path: String): OutputStream {
        val file = directory / transformPath(path)

        require(file.canonicalFile.startsWith(directory.canonicalFile)) {
            "Path '$path' is not in directory '${directory.invariantSeparatorsPath}'."
        }

        file.parentFile.safeMkdirs()

        return file.outputStream()
    }

    @Synchronized
    override fun write(path: String, inputStream: InputStream) {
        safeOutputStream(path).use { outputStream ->
            inputStream.use { it.copyTo(outputStream) }
        }
    }

    @Synchronized
    override fun delete(path: String): Boolean = directory.resolve(transformPath(path)).delete()
}
