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

package com.here.ort.spdx

import java.io.File
import java.io.IOException
import java.util.EnumSet

import org.apache.commons.codec.binary.Hex
import org.apache.commons.codec.digest.DigestUtils

/**
 * A list of globs that match typical license file names.
 */
val LICENSE_FILE_NAMES = listOf(
        "LICENSE*",
        "LICENCE*",
        "UNLICENSE",
        "UNLICENCE",
        "COPYING*",
        "COPYRIGHT",
        "PATENTS"
)

/**
 * A list of globs that match file names which are not license files but typically trigger false-positives.
 */
val NON_LICENSE_FILENAMES = listOf(
        "HERE_NOTICE",
        "META-INF/DEPENDENCIES"
)

/**
 * Calculate the [SPDX package verification code][1] for a list of known SHA1s of files.
 *
 * [1]: https://spdx.github.io/spdx-spec/chapters/3-package-information.html#39-package-verification-code-
 */
@JvmName("calculatePackageVerificationCodeForStrings")
fun calculatePackageVerificationCode(sha1sums: List<String>): String =
        Hex.encodeHexString(sha1sums.sorted().fold(DigestUtils.getSha1Digest()) { digest, sha1sum ->
            DigestUtils.updateDigest(digest, sha1sum)
        }.digest())

/**
 * Calculate the [SPDX package verification code][1] for a list of files.
 *
 * [1]: https://spdx.github.io/spdx-spec/chapters/3-package-information.html#39-package-verification-code-
 */
@JvmName("calculatePackageVerificationCodeForFiles")
fun calculatePackageVerificationCode(files: List<File>) =
        calculatePackageVerificationCode(files.map { file ->
            file.inputStream().use { DigestUtils.sha1Hex(it) }
        })

/**
 * A Kotlin-style convenience function to replace EnumSet.of() and EnumSet.noneOf().
 */
inline fun <reified T : Enum<T>> enumSetOf(vararg elems: T): EnumSet<T> =
        EnumSet.noneOf(T::class.java).apply { addAll(elems) }

/**
 * Retrieve the full text for the license with the provided SPDX [id], including "LicenseRefs". If [handleExceptions] is
 * enabled, the [id] may also refer to an exception instead of a license.
 */
fun getLicenseText(id: String, handleExceptions: Boolean = false) =
        if (id.startsWith("LicenseRef-")) {
            object {}.javaClass.getResource("/licenserefs/$id")?.readText()
        } else {
            SpdxLicense.forId(id)?.text ?: SpdxLicenseException.forId(id)?.text?.takeIf { handleExceptions }
        } ?: throw IOException("No license text found for id '$id'.")
