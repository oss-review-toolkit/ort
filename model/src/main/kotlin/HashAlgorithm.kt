/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

enum class HashAlgorithm(private vararg val aliases: String) {
    UNKNOWN("", "UNKNOWN"),
    MD5("MD5"),
    SHA1("SHA-1", "SHA1"),
    SHA256("SHA-256", "SHA256"),
    SHA384("SHA-384", "SHA384"),
    SHA512("SHA-512", "SHA512");

    companion object {
        @JsonCreator
        @JvmStatic
        fun fromString(alias: String) =
                enumValues<HashAlgorithm>().find {
                    alias.toUpperCase() in it.aliases
                } ?: UNKNOWN

        fun fromHash(hash: String): HashAlgorithm {
            if (hash.isBlank()) return HashAlgorithm.UNKNOWN

            return when (hash.length) {
                128 -> SHA512
                96 -> SHA384
                64 -> SHA256
                40 -> SHA1
                32 -> MD5
                else -> UNKNOWN
            }
        }
    }

    @JsonValue
    override fun toString(): String {
        return aliases.first()
    }
}
