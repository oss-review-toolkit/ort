/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.utils.spdx

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

import java.io.File
import java.net.URL
import java.security.MessageDigest

import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.VCS_DIRECTORIES
import org.ossreviewtoolkit.utils.common.calculateHash
import org.ossreviewtoolkit.utils.common.encodeHex
import org.ossreviewtoolkit.utils.common.isSymbolicLink
import org.ossreviewtoolkit.utils.common.realFile
import org.ossreviewtoolkit.utils.spdx.SpdxConstants.LICENSE_REF_PREFIX

/**
 * A comparator that sorts parent paths before child paths.
 */
internal val PATH_STRING_COMPARATOR = compareBy<String>({ path -> path.count { it == '/' } }, { it })

/**
 * A mapper to read license mapping from YAML resource files.
 */
internal val yamlMapper = YAMLMapper().registerKotlinModule()

/**
 * The directory that contains the ScanCode license texts. This is located using a heuristic based on the path of the
 * ScanCode binary.
 */
val scanCodeLicenseTextDir by lazy {
    val scanCodeExeDir = Os.getPathFromEnvironment("scancode")?.realFile()?.parentFile

    val pythonBinDir = listOf("bin", "Scripts")
    val scanCodeBaseDir = scanCodeExeDir?.takeUnless { it.name in pythonBinDir } ?: scanCodeExeDir?.parentFile

    scanCodeBaseDir?.walkTopDown()?.find { it.isDirectory && it.endsWith("licensedcode/data/licenses") }
}

/**
 * Calculate the [SPDX package verification code][1] for a list of [known SHA1s][sha1sums] of files and [excludes].
 *
 * [1]: https://spdx.dev/spdx_specification_2_0_html#h.2p2csry
 */
@JvmName("calculatePackageVerificationCodeForStrings")
fun calculatePackageVerificationCode(sha1sums: Sequence<String>, excludes: Sequence<String> = emptySequence()): String {
    val sha1sum = sha1sums.sorted().fold(MessageDigest.getInstance("SHA-1")) { digest, sha1sum ->
        digest.apply { update(sha1sum.toByteArray()) }
    }.digest().encodeHex()

    return if (excludes.none()) {
        sha1sum
    } else {
        "$sha1sum (excludes: ${excludes.joinToString()})"
    }
}

/**
 * Calculate the [SPDX package verification code][1] for a list of [files] and paths of [excludes].
 *
 * [1]: https://spdx.dev/spdx_specification_2_0_html#h.2p2csry
 */
@JvmName("calculatePackageVerificationCodeForFiles")
fun calculatePackageVerificationCode(files: Sequence<File>, excludes: Sequence<String> = emptySequence()): String =
    calculatePackageVerificationCode(files.map { calculateHash(it).encodeHex() }, excludes)

/**
 * Calculate the [SPDX package verification code][1] for all files in a [directory]. If [directory] points to a file
 * instead of a directory the verification code for the single file is returned.
 * All files with the extension ".spdx" are automatically excluded from the generated code. Additionally, files from
 * [VCS directories][VCS_DIRECTORIES] are excluded.
 *
 * [1]: https://spdx.dev/spdx_specification_2_0_html#h.2p2csry
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
 * Retrieve the full text for the license with the provided SPDX [id], including "LicenseRefs". If [handleExceptions] is
 * enabled, the [id] may also refer to an exception instead of a license. If [licenseTextDirectories] is provided, the
 * contained directories are searched in order for the license text if and only if the license text is not known by ORT.
 */
fun getLicenseText(
    id: String,
    handleExceptions: Boolean = false,
    licenseTextDirectories: List<File> = emptyList()
): String? = getLicenseTextReader(id, handleExceptions, addScanCodeLicenseTextsDir(licenseTextDirectories))?.invoke()

fun hasLicenseText(
    id: String,
    handleExceptions: Boolean = false,
    licenseTextDirectories: List<File> = emptyList()
): Boolean = getLicenseTextReader(id, handleExceptions, addScanCodeLicenseTextsDir(licenseTextDirectories)) != null

fun getLicenseTextReader(
    id: String,
    handleExceptions: Boolean = false,
    licenseTextDirectories: List<File> = emptyList()
): (() -> String)? {
    return if (id.startsWith(LICENSE_REF_PREFIX)) {
        getLicenseTextResource(id)?.let { { it.readText() } }
            ?: addScanCodeLicenseTextsDir(licenseTextDirectories).firstNotNullOfOrNull {
                getLicenseTextFile(id, it)?.let { file -> { file.readText() } }
            }
    } else {
        SpdxLicense.forId(id.removeSuffix("+"))?.let { { it.text } }
            ?: SpdxLicenseException.forId(id)?.takeIf { handleExceptions }?.let { { it.text } }
    }
}

private fun getLicenseTextResource(id: String): URL? =
    object {}.javaClass.getResource("/licenserefs/$id")

private val LICENSE_REF_FILENAME_REGEX by lazy { Regex("^LicenseRef-\\w+-") }

private fun getLicenseTextFile(id: String, dir: File): File? =
    id.replace(LICENSE_REF_FILENAME_REGEX, "").let { idWithoutLicenseRefNamespace ->
        listOfNotNull(
            id,
            id.removePrefix("LicenseRef-"),
            idWithoutLicenseRefNamespace,
            "$idWithoutLicenseRefNamespace.LICENSE",
            "x11-xconsortium_veillard.LICENSE".takeIf {
                // Work around for https://github.com/nexB/scancode-toolkit/issues/2813.
                id == "LicenseRef-scancode-x11-xconsortium-veillard"
            }
        ).firstNotNullOfOrNull { filename ->
            dir.resolve(filename).takeIf { it.isFile }
        }
    }

private fun addScanCodeLicenseTextsDir(licenseTextDirectories: List<File>): List<File> =
    (listOfNotNull(scanCodeLicenseTextDir) + licenseTextDirectories).distinct()
