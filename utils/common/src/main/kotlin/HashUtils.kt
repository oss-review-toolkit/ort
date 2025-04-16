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

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * Calculate the [digest] on the data from the given [file].
 */
fun calculateHash(file: File, digest: MessageDigest = MessageDigest.getInstance("SHA-1")): ByteArray =
    file.inputStream().use { calculateHash(it, digest) }

/**
 * Calculate the [digest] on the data from the given [inputStream]. The caller is responsible for closing the stream.
 */
fun calculateHash(inputStream: InputStream, digest: MessageDigest = MessageDigest.getInstance("SHA-1")): ByteArray {
    // 4MB has been chosen rather arbitrarily, hoping that it provides good performance while not consuming a
    // lot of memory at the same time, also considering that this function could potentially be run on multiple
    // threads in parallel.
    val buffer = ByteArray(4.mebibytes.toInt())

    var length: Int
    while (inputStream.read(buffer).also { length = it } > 0) {
        digest.update(buffer, 0, length)
    }

    return digest.digest()
}
