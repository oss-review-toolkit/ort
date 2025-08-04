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

package org.ossreviewtoolkit.model

import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.util.StdConverter

import java.io.File

import kotlin.io.encoding.Base64

/**
 * A class that bundles a hash algorithm with its hash value.
 */
data class Hash(
    /**
     * The value calculated using the hash algorithm.
     */
    @JsonSerialize(converter = StringLowercaseConverter::class)
    val value: String,

    /**
     * The algorithm used to calculate the hash value.
     */
    val algorithm: HashAlgorithm
) {
    companion object {
        /**
         * A constant to specify that no hash value (and thus also no hash algorithm) is provided.
         */
        val NONE = Hash(HashAlgorithm.NONE.toString(), HashAlgorithm.NONE)

        /**
         * Create a [Hash] instance from a known hash [value]. If the [HashAlgorithm] cannot be determined, the original
         * [value] is returned with [HashAlgorithm.UNKNOWN], or with [HashAlgorithm.NONE] if the value is blank.
         */
        fun create(value: String): Hash {
            val splitValue = value.split('-')
            return if (splitValue.size == 2) {
                // Support Subresource Integrity (SRI) hashes, see
                // https://w3c.github.io/webappsec-subresource-integrity/
                Hash(
                    value = Base64.decode(splitValue.last()).toHexString(),
                    algorithm = HashAlgorithm.fromString(splitValue.first())
                )
            } else {
                Hash(value, HashAlgorithm.create(value))
            }
        }
    }

    init {
        require(!algorithm.isVerifiable || (algorithm.size == value.length && value.all { it.isLetterOrDigit() })) {
            "'$value' is not a $algorithm hash."
        }
    }

    /**
     * Construct a [Hash] instance from hash [value] and [algorithm] strings.
     */
    constructor(value: String, algorithm: String) : this(value, HashAlgorithm.fromString(algorithm))

    /**
     * Return the hash in Support Subresource Integrity (SRI) format.
     */
    fun toSri() = algorithm.name.lowercase() + "-" + Base64.encode(value.hexToByteArray())

    /**
     * Verify that the [file] matches this hash.
     */
    fun verify(file: File): Boolean {
        require(algorithm in HashAlgorithm.VERIFIABLE) {
            "Cannot verify algorithm '$algorithm'. Supported algorithms are ${HashAlgorithm.VERIFIABLE}."
        }

        return algorithm.calculate(file).equals(value, ignoreCase = true)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Hash) return false
        return algorithm == other.algorithm && value.equals(other.value, ignoreCase = true)
    }

    override fun hashCode() =
        value.foldIndexed(algorithm.hashCode()) { index, acc, c ->
            acc + 31 * (index + (c.digitToIntOrNull() ?: 0))
        }
}

private class StringLowercaseConverter : StdConverter<String, String>() {
    override fun convert(value: String): String = value.lowercase()
}
