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

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

import org.ossreviewtoolkit.utils.common.calculateHash

/**
 * An enum of supported hash algorithms. Each algorithm has one or more [aliases] associated to it, where the first
 * alias is the definite name.
 */
enum class HashAlgorithm(vararg val aliases: String, val emptyValue: String, val isVerifiable: Boolean = true) {
    /**
     * No hash algorithm.
     */
    NONE("", emptyValue = "", isVerifiable = false),

    /**
     * An unknown hash algorithm.
     */
    UNKNOWN("UNKNOWN", emptyValue = "", isVerifiable = false),

    /**
     * The Message-Digest 5 hash algorithm, see [MD5](http://en.wikipedia.org/wiki/MD5).
     */
    MD5("MD5", emptyValue = "d41d8cd98f00b204e9800998ecf8427e"),

    /**
     * The Secure Hash Algorithm 1, see [SHA-1](https://en.wikipedia.org/wiki/SHA-1).
     */
    SHA1("SHA-1", "SHA1", emptyValue = "da39a3ee5e6b4b0d3255bfef95601890afd80709"),

    /**
     * The Secure Hash Algorithm 2 with 256 bits, see [SHA-256](https://en.wikipedia.org/wiki/SHA-256).
     */
    SHA256("SHA-256", "SHA256", emptyValue = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"),

    /**
     * The Secure Hash Algorithm 2 with 384 bits, see [SHA-384](https://en.wikipedia.org/wiki/SHA-384).
     */
    SHA384(
        "SHA-384", "SHA384",
        emptyValue = "38b060a751ac96384cd9327eb1b1e36a21fdb71114be07434c0cc7bf63f6e1da274edebfe76f65fbd51ad2f14898b95b"
    ),

    /**
     * The Secure Hash Algorithm 2 with 512 bits, see [SHA-512](https://en.wikipedia.org/wiki/SHA-512).
     */
    SHA512(
        "SHA-512", "SHA512",
        emptyValue = "cf83e1357eefb8bdf1542850d66d8007d620e4050b5715dc83f4a921d36ce9ce" +
            "47d0d13c5d85f2b0ff8318d2877eec2f63b931bd47417a81a538327af927da3e"
    ),

    /**
     * The Secure Hash Algorithm 1, but calculated on a Git "blob" object, see
     * - https://git-scm.com/book/en/v2/Git-Internals-Git-Objects#_object_storage
     * - https://docs.softwareheritage.org/devel/swh-model/persistent-identifiers.html#git-compatibility
     */
    SHA1GIT(
        "SHA-1-GIT", "SHA1-GIT", "SHA1GIT", "SWHID",
        emptyValue = "e69de29bb2d1d6434b8b29ae775ad8c2e48c5391"
    ) {
        override fun getMessageDigest(size: Long): MessageDigest =
            MessageDigest.getInstance(SHA1.toString()).apply {
                val header = "blob $size\u0000"
                update(header.toByteArray())
            }
    };

    companion object {
        /**
         * The list of algorithms that can be verified.
         */
        val VERIFIABLE = HashAlgorithm.entries.filter { it.isVerifiable }

        /**
         * Create a hash algorithm from one of its [alias] names.
         */
        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        @JvmStatic
        fun fromString(alias: String): HashAlgorithm =
            HashAlgorithm.entries.find {
                alias.uppercase() in it.aliases
            } ?: UNKNOWN

        /**
         * Create a hash algorithm from a hash [value].
         */
        fun create(value: String): HashAlgorithm {
            if (value.isBlank()) return NONE
            return HashAlgorithm.entries.find { it.size == value.length } ?: UNKNOWN
        }
    }

    /**
     * The size of a hexadecimal hash value string for this algorithm.
     */
    val size = emptyValue.length

    /**
     * Convert the hash algorithm to a string representation.
     */
    @JsonValue
    override fun toString(): String = aliases.first()

    /**
     * Return the hexadecimal digest of this hash for the given [file].
     */
    fun calculate(file: File): String = file.inputStream().use { calculate(it, file.length()) }

    /**
     * Return the hexadecimal digest of this hash for the given [bytes].
     */
    fun calculate(bytes: ByteArray): String = bytes.inputStream().use { calculate(it, bytes.size.toLong()) }

    /**
     * Return the hexadecimal digest of this hash for the given [resourceName].
     */
    fun calculate(resourceName: String): String? {
        val resource = javaClass.getResource(resourceName)
        val size = resource?.openConnection()?.contentLengthLong ?: return null
        return resource.openStream().use { calculate(it, size) }
    }

    /**
     * Return the message digest to use for this [HashAlgorithm], which might depend on the [size].
     */
    protected open fun getMessageDigest(size: Long): MessageDigest =
        // Disregard the size in the standard case.
        MessageDigest.getInstance(toString())

    /**
     * Return the hexadecimal digest of this hash for the given [inputStream] and [size]. The caller is responsible for
     * closing the stream.
     */
    private fun calculate(inputStream: InputStream, size: Long): String =
        calculateHash(inputStream, getMessageDigest(size)).toHexString()
}
