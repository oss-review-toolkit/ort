/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

/**
 * A class that wraps a [CharSequence] and overrides some functions to return a configurable mask string
 * instead of the actual content. The main use case for this class is to prevent that sensitive information like
 * passwords are visible in log output. The class can be used everywhere a [CharSequence] is accepted.
 */
data class MaskedString(
    /** The wrapped string. */
    val value: CharSequence,

    /** A string that is returned by the toString method instead of the actual value. */
    val mask: String = DEFAULT_MASK
) : CharSequence by mask {
    companion object {
        const val DEFAULT_MASK = "*****"
    }

    override fun toString() = mask
}

/**
 * Return an array with strings from the given [args]. If one of the input strings is a [MaskedString], include its
 * actual value. So, this function can be used to access the unmasked strings.
 */
fun unmaskedStrings(vararg args: CharSequence): Array<out String> =
    args.map { s ->
        when (s) {
            is MaskedString -> s.value.toString()
            else -> s.toString()
        }
    }.toTypedArray()

/**
 * Convert this [CharSequence] to a [MaskedString] which uses the provided [mask].
 */
fun CharSequence.masked(mask: String = MaskedString.DEFAULT_MASK): MaskedString = MaskedString(this, mask)
