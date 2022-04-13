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

package org.ossreviewtoolkit.model

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.deser.std.StdDeserializer

import java.io.File
import java.util.Base64

import org.ossreviewtoolkit.utils.common.decodeHex
import org.ossreviewtoolkit.utils.common.encodeHex

/**
 * A class that bundles a hash algorithm with its hash value.
 */
@JsonDeserialize(using = HashDeserializer::class)
data class Hash(
    /**
     * The value calculated using the hash algorithm.
     */
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
         * Create a [Hash] instance from a known hash [value]. If the [HashAlgorithm] cannot be determined,
         * [HashAlgorithm.UNKNOWN] along with the original [value] is returned.
         */
        fun create(value: String): Hash {
            val splitValue = value.split('-')
            return if (splitValue.size == 2) {
                // Support Subresource Integrity (SRI) hashes, see
                // https://w3c.github.io/webappsec-subresource-integrity/
                Hash(
                    value = Base64.getDecoder().decode(splitValue.last()).encodeHex(),
                    algorithm = HashAlgorithm.fromString(splitValue.first())
                )
            } else {
                Hash(value, HashAlgorithm.create(value))
            }
        }

        /**
         * Create a [Hash] instance from a known hash [value] and [algorithm]. This is mostly used for deserialization
         * to verify the algorithm matches the one determined by the value.
         */
        fun create(value: String, algorithm: String): Hash =
            create(value).also { hash ->
                require(hash.algorithm == HashAlgorithm.fromString(algorithm)) {
                    "'$value' is not a $algorithm hash."
                }
            }
    }

    /**
     * Return the hash in Support Subresource Integrity (SRI) format.
     */
    fun toSri() = "${algorithm.toString().lowercase()}-${Base64.getEncoder().encodeToString(value.decodeHex())}"

    /**
     * Verify that the [file] matches this hash.
     */
    fun verify(file: File): Boolean {
        require(algorithm in HashAlgorithm.VERIFIABLE) {
            "Cannot verify algorithm '$algorithm'. Supported algorithms are ${HashAlgorithm.VERIFIABLE}."
        }

        return algorithm.calculate(file).equals(value, ignoreCase = true)
    }
}

private class HashDeserializer : StdDeserializer<Hash>(Hash::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Hash {
        val node = p.codec.readTree<JsonNode>(p)
        return if (node.isTextual) {
            val hashValue = node.textValue()
            if (p.nextFieldName() == "hash_algorithm") {
                val hashAlgorithm = p.nextTextValue()
                Hash.create(hashValue, hashAlgorithm)
            } else {
                Hash.create(hashValue)
            }
        } else {
            Hash.create(node["value"].textValue(), node["algorithm"].textValue())
        }
    }
}
