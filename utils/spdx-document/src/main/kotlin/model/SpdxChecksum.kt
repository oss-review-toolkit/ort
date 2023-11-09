/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.utils.spdxdocument.model

/**
 * A checksum for an [SpdxFile].
 * See https://spdx.github.io/spdx-spec/v2.3/file-information/#84-file-checksum-field.
 */
data class SpdxChecksum(
    /**
     * The checksum algorithm.
     */
    val algorithm: Algorithm,

    /**
     * The lower case hexadecimal checksum value.
     */
    val checksumValue: String
) {
    companion object {
        internal val HEX_SYMBOLS_REGEX = "^[0-9a-f]+$".toRegex()
    }

    enum class Algorithm(val checksumHexDigits: Int, val isSpdx23: Boolean = false) {
        ADLER32(8, true),
        BLAKE2B_256(64, true),
        BLAKE2B_384(96, true),
        BLAKE2B_512(128, true),
        BLAKE3(64, true),
        MD2(32),
        MD4(32),
        MD5(32),
        MD6(-1),
        SHA1(40),
        SHA224(56),
        SHA256(64),
        SHA384(96),
        SHA512(128),
        SHA3_256(64, true),
        SHA3_384(96, true),
        SHA3_512(128, true)
    }

    init {
        validate()
    }

    fun validate(): SpdxChecksum =
        apply {
            require(checksumValue.isNotBlank()) { "The checksum value must not be blank." }

            require(checksumValue.matches(HEX_SYMBOLS_REGEX)) {
                "The checksum value must only contain lower case hexadecimal symbols."
            }

            require(algorithm.checksumHexDigits == -1 || checksumValue.length == algorithm.checksumHexDigits) {
                "Expected a checksum value with ${algorithm.checksumHexDigits} hexadecimal symbols, but found " +
                    "${checksumValue.length}."
            }
        }
}
