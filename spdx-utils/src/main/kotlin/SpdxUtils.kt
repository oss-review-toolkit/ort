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
import java.nio.file.FileSystems
import java.security.MessageDigest
import java.util.EnumSet

/**
 * A list of globs that match typical license file names.
 */
val LICENSE_FILE_NAMES = listOf(
    "license*",
    "licence*",
    "unlicense",
    "unlicence",
    "copying*",
    "copyright",
    "patents",
    "readme*"
).flatMap { listOf(it, it.toUpperCase(), it.capitalize()) }

val LICENSE_FILE_MATCHERS = LICENSE_FILE_NAMES.map { FileSystems.getDefault().getPathMatcher("glob:$it") }

/**
 * A list of globs that match file names which are not license files but typically trigger false-positives.
 */
val NON_LICENSE_FILENAMES = listOf(
    "HERE_NOTICE",
    "META-INF/DEPENDENCIES"
)

/**
 * Return a string of hexadecimal digits representing the bytes in the array.
 */
private fun ByteArray.toHexString(): String = joinToString("") { String.format("%02x", it) }

/**
 * Calculate the [SPDX package verification code][1] for a list of known SHA1s of files.
 *
 * [1]: https://spdx.github.io/spdx-spec/chapters/3-package-information.html#39-package-verification-code-
 */
@JvmName("calculatePackageVerificationCodeForStrings")
fun calculatePackageVerificationCode(sha1sums: List<String>): String =
    sha1sums.sorted().fold(MessageDigest.getInstance("SHA-1")) { digest, sha1sum ->
        digest.apply { update(sha1sum.toByteArray()) }
    }.digest().toHexString()

/**
 * Calculate the [SPDX package verification code][1] for a list of files.
 *
 * [1]: https://spdx.github.io/spdx-spec/chapters/3-package-information.html#39-package-verification-code-
 */
@JvmName("calculatePackageVerificationCodeForFiles")
fun calculatePackageVerificationCode(files: List<File>) =
    MessageDigest.getInstance("SHA-1").let { digest ->
        calculatePackageVerificationCode(files.map { file ->
            file.inputStream().use { digest.digest(it.readBytes()).toHexString() }
        })
    }

/**
 * A Kotlin-style convenience function to replace EnumSet.of() and EnumSet.noneOf().
 */
inline fun <reified T : Enum<T>> enumSetOf(vararg elems: T): EnumSet<T> =
    EnumSet.noneOf(T::class.java).apply { addAll(elems) }

/**
 * Retrieve the full text for the license with the provided SPDX [id], including "LicenseRefs". If [handleExceptions] is
 * enabled, the [id] may also refer to an exception instead of a license. If [customLicenseTextsDir] is provided the
 * license text is retrieved from that directory if and only if the license text is not known by ORT.
 */
fun getLicenseText(id: String, handleExceptions: Boolean = false, customLicenseTextsDir: File? = null): String? =
    if (id.startsWith("LicenseRef-")) {
        getLicenseTextFromResource(id) ?: customLicenseTextsDir?.let { getLicenseTextFromDirectory(id, it) }
    } else {
        SpdxLicense.forId(id)?.text ?: SpdxLicenseException.forId(id)?.text?.takeIf { handleExceptions }
    }

private fun getLicenseTextFromResource(id: String): String? =
    object {}.javaClass.getResource("/licenserefs/$id")?.readText()

private fun getLicenseTextFromDirectory(id: String, dir: File): String? =
    dir.resolve(id).let { if (it.isFile) it.readText() else null }
