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

@file:Suppress("TooManyFunctions")

package org.ossreviewtoolkit.spdx

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

import java.io.File
import java.net.URL
import java.security.MessageDigest
import java.util.EnumSet

import org.ossreviewtoolkit.spdx.SpdxExpression.Strictness
import org.ossreviewtoolkit.spdx.model.SpdxConstants

/**
 * A list of globs that match file names which are not license files but typically trigger false-positives.
 */
val NON_LICENSE_FILENAMES = listOf(
    "HERE_NOTICE",
    "META-INF/DEPENDENCIES"
)

/**
 * A list of directories used by version control systems to store metadata.
 */
internal val VCS_DIRECTORIES = listOf(
    ".git",
    ".hg",
    ".repo",
    ".svn",
    "CVS"
)

/**
 * A comparator that sorts parent paths before child paths.
 */
internal val PATH_STRING_COMPARATOR = compareBy<String>({ path -> path.count { it == '/' } }, { it })

/**
 * A mapper to read license mapping from YAML resource files.
 */
internal val yamlMapper = YAMLMapper().registerKotlinModule()

/**
 * Return a string of hexadecimal digits representing the bytes in the array.
 */
private fun ByteArray.toHexString(): String = joinToString("") { String.format("%02x", it) }

/**
 * Calculate the [SPDX package verification code][1] for a list of [known SHA1s][sha1sums] of files and [excludes].
 *
 * [1]: https://spdx.org/spdx_specification_2_0_html#h.2p2csry
 */
@JvmName("calculatePackageVerificationCodeForStrings")
fun calculatePackageVerificationCode(sha1sums: Sequence<String>, excludes: Sequence<String> = emptySequence()): String {
    val sha1sum = sha1sums.sorted().fold(MessageDigest.getInstance("SHA-1")) { digest, sha1sum ->
        digest.apply { update(sha1sum.toByteArray()) }
    }.digest().toHexString()

    return if (excludes.none()) {
        sha1sum
    } else {
        "$sha1sum (excludes: ${excludes.joinToString()})"
    }
}

/**
 * Calculate the [SPDX package verification code][1] for a list of [files] and paths of [excludes].
 *
 * [1]: https://spdx.org/spdx_specification_2_0_html#h.2p2csry
 */
@JvmName("calculatePackageVerificationCodeForFiles")
fun calculatePackageVerificationCode(files: Sequence<File>, excludes: Sequence<String> = emptySequence()): String =
    calculatePackageVerificationCode(files.map { sha1sum(it) }, excludes)

/**
 * Return the SHA-1 sum of the given file as hex string.
 */
private fun sha1sum(file: File): String =
    file.inputStream().use { inputStream ->
        // 4MB has been chosen rather arbitrary hoping that it provides a good enough performance while not consuming
        // a lot of memory at the same time, also considering that this function could potentially be run on multiple
        // threads in parallel.
        val buffer = ByteArray(4 * 1024 * 1024)
        val digest = MessageDigest.getInstance("SHA-1")

        var length: Int
        while (inputStream.read(buffer).also { length = it } > 0) {
            digest.update(buffer, 0, length)
        }

        digest.digest().toHexString()
    }

/**
 * Calculate the [SPDX package verification code][1] for all files in a [directory]. If [directory] points to a file
 * instead of a directory the verification code for the single file is returned.
 * All files with the extension ".spdx" are automatically excluded from the generated code. Additionally files from
 * [VCS directories][VCS_DIRECTORIES] are excluded.
 *
 * [1]: https://spdx.org/spdx_specification_2_0_html#h.2p2csry
 */
@JvmName("calculatePackageVerificationCodeForDirectory")
fun calculatePackageVerificationCode(directory: File): String {
    val allFiles = directory.walk().onEnter { !it.isSymbolicLink() }.filter { !it.isSymbolicLink() && it.isFile }
    val spdxFiles = allFiles.filter { it.extension == "spdx" }
    val files = allFiles.filter { it.extension != "spdx" }

    // Sort the list of files to show the files in a directory before the files in its subdirectories. This can be
    // omitted once breadth-first search is available in Kotlin: https://github.com/JetBrains/kotlin/pull/2232
    val filteredFiles = files.filter {
        val relativePath = it.relativeTo(directory).invariantSeparatorsPath
        VCS_DIRECTORIES.none { vcs -> relativePath.startsWith("$vcs/") }
    }

    val sortedExcludes = spdxFiles.map { "./${it.relativeTo(directory).invariantSeparatorsPath}" }
            .sortedWith(PATH_STRING_COMPARATOR)

    return calculatePackageVerificationCode(filteredFiles, sortedExcludes)
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
    getLicenseTextReader(id, handleExceptions, customLicenseTextsDir)?.invoke()

fun hasLicenseText(id: String, handleExceptions: Boolean = false, customLicenseTextsDir: File? = null): Boolean =
    getLicenseTextReader(id, handleExceptions, customLicenseTextsDir) != null

fun getLicenseTextReader(
    id: String,
    handleExceptions: Boolean = false,
    customLicenseTextsDir: File? = null
): (() -> String)? =
    if (id.startsWith("LicenseRef-")) {
        getLicenseTextResource(id)?.let { { it.readText() } }
            ?: customLicenseTextsDir?.let { getLicenseTextFile(id, it)?.let { file -> { file.readText() } } }
    } else {
        SpdxLicense.forId(id)?.let { { it.text } }
            ?: SpdxLicenseException.forId(id)?.takeIf { handleExceptions }?.let { { it.text } }
    }

/**
 * Return true if and only if this String can be successfully parsed to a [SpdxExpression].
 */
internal fun String.isSpdxExpression(): Boolean =
    runCatching { SpdxExpression.parse(this, Strictness.ALLOW_DEPRECATED) }.isSuccess

/**
 * Return true if and only if this String can be successfully parsed to an [SpdxExpression] or if it equals
 * [org.ossreviewtoolkit.spdx.model.SpdxConstants.NONE] or [org.ossreviewtoolkit.spdx.model.SpdxConstants.NOASSERTION].
 */
internal fun String.isSpdxExpressionOrNotPresent(): Boolean =
    SpdxConstants.isNotPresent(this) || isSpdxExpression()

private fun getLicenseTextResource(id: String): URL? =
    object {}.javaClass.getResource("/licenserefs/$id")

private fun getLicenseTextFile(id: String, dir: File): File? =
    dir.resolve(id).takeIf { it.isFile }
