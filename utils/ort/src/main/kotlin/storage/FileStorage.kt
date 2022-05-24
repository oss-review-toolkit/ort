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

import java.io.InputStream

/**
 * A storage for files, like a local directory or a remote server.
 */
interface FileStorage {
    /**
     * Return whether the given [path] exists or not.
     */
    fun exists(path: String): Boolean

    /**
     * Read the file at [path]. It is the caller's responsibility to close the returned [InputStream] after consuming
     * it.
     */
    fun read(path: String): InputStream

    /**
     * Write the data from [inputStream] to the file at [path]. If the file already exists it is overwritten. The
     * provided [inputStream] is closed after writing it to the file.
     */
    fun write(path: String, inputStream: InputStream)
}
