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

import ch.frankel.slf4k.*

import com.here.ort.utils.hash
import com.here.ort.utils.log
import com.here.ort.utils.toHexString

import java.io.File
import java.util.Base64

data class Hash(
        val algorithm: HashAlgorithm,
        val value: String
) {
    companion object {
        fun fromValue(value: String): Hash {
            val splitValue = value.split('-')
            return if (splitValue.count() == 2) {
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

    fun verify(file: File): Boolean {
        val hash = when (algorithm) {
            HashAlgorithm.UNKNOWN -> {
                log.warn { "Unknown hash algorithm." }
                ""
            }
            else -> file.hash(algorithm.toString())
        }

        return hash == value
    }
}
