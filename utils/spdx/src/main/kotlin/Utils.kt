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

package org.ossreviewtoolkit.utils.spdx

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

import java.io.File
import java.security.MessageDigest

import org.ossreviewtoolkit.utils.common.PATH_STRING_COMPARATOR
import org.ossreviewtoolkit.utils.common.VCS_DIRECTORIES
import org.ossreviewtoolkit.utils.common.calculateHash
import org.ossreviewtoolkit.utils.common.isSymbolicLink

/**
 * A mapper to read license mapping from YAML resource files.
 */
internal val yamlMapper = YAMLMapper().registerKotlinModule()

/**
 * Calculate the [SPDX package verification code][1] for a list of [known SHA1s][sha1sums] of files and [excludes].
 *
 * [1]: https://spdx.dev/spdx_specification_2_0_html#h.2p2csry
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
 * [1]: https://spdx.dev/spdx_specification_2_0_html#h.2p2csry
 */
@JvmName("calculatePackageVerificationCodeForFiles")
fun calculatePackageVerificationCode(files: Sequence<File>, excludes: Sequence<String> = emptySequence()): String =
    calculatePackageVerificationCode(files.map { calculateHash(it).toHexString() }, excludes)

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
    val allFiles = directory.walk()
        .onEnter { !it.isSymbolicLink && it.name !in VCS_DIRECTORIES }
        .filter { !it.isSymbolicLink && it.isFile }

    // Filter twice instead of using "partition" as the latter does not return sequences.
    val spdxFiles = allFiles.filter { it.extension == "spdx" }
    val files = allFiles.filter { it.extension != "spdx" }

    // Sort the list of files to show the files in a directory before the files in its subdirectories. This can be
    // omitted once breadth-first search is available in Kotlin: https://youtrack.jetbrains.com/issue/KT-18629
    val sortedExcludes = spdxFiles.map { "./${it.relativeTo(directory).invariantSeparatorsPath}" }
        .sortedWith(PATH_STRING_COMPARATOR)

    return calculatePackageVerificationCode(files, sortedExcludes)
}
