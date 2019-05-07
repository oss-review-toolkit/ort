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

package com.here.ort.model

import com.here.ort.utils.hash
import com.here.ort.utils.toHexString

import java.io.File
import java.util.Base64

/**
 * A class that bundles a hash algorithm with its hash value.
 */
data class Hash(
    /**
     * The algorithm used to calculate the hash value.
     */
    val algorithm: HashAlgorithm,

    /**
     * The value calculated using the hash algorithm.
     */
    val value: String
) {
    companion object {
        /**
         * A constant to specify that no hash value (and thus also no hash algorithm) is provided.
         */
        val NONE = Hash(HashAlgorithm.NONE, HashAlgorithm.NONE.toString())


        /**
         * The set of algorithms that cannot be verified.
         */
        val UNVERIFIABLE = setOf(HashAlgorithm.NONE, HashAlgorithm.UNKNOWN)

        /**
         * The list of algorithms that can be verified.
         */
        val VERIFIABLE = enumValues<HashAlgorithm>().toSet() - UNVERIFIABLE

        /**
         * Create a hash from it a [value].
         */
        fun fromValue(value: String): Hash {
            val splitValue = value.split('-')
            return if (splitValue.size == 2) {
                // Support Subresource Integrity (SRI) hashes, see
                // https://w3c.github.io/webappsec-subresource-integrity/
                Hash(
                    algorithm = HashAlgorithm.fromString(splitValue.first()),
                    value = Base64.getDecoder().decode(splitValue.last()).toHexString()
                )
            } else {
                Hash(HashAlgorithm.fromHash(value), value)
            }
        }
    }

    /**
     * Verify that the [file] matches this hash.
     */
    fun verify(file: File): Boolean {
        require(algorithm in VERIFIABLE) {
            "Cannot verify algorithm '$algorithm'. Supported algorithms are $VERIFIABLE."
        }

        return file.hash(algorithm.toString()) == value
    }
}
