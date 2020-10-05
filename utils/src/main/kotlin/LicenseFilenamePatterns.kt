/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.utils

import java.io.File

private fun List<String>.generateCapitalizationVariants() = flatMap { listOf(it, it.toUpperCase(), it.capitalize()) }

object LicenseFilenamePatterns {
    /**
     * A list of globs that match default license file names.
     */
    val LICENSE_FILENAMES = listOf(
        "copying*",
        "copyright",
        "licence*",
        "license*",
        "*.licence",
        "*.license",
        "unlicence",
        "unlicense"
    ).generateCapitalizationVariants()

    /**
     * A list of globs that match default patent file names.
     */
    val PATENT_FILENAMES = listOf(
        "patents"
    ).generateCapitalizationVariants()

    /**
     * A list of globs that match files that often define the root license of a project, but are no license files and
     * are therefore not contained in [LICENSE_FILENAMES].
     */
    val ROOT_LICENSE_FILENAMES = listOf(
        "readme*"
    ).generateCapitalizationVariants()

    /**
     * A list of globs that match all kind of license file names, equaling the union of [LICENSE_FILENAMES],
     * [PATENT_FILENAMES] and [ROOT_LICENSE_FILENAMES].
     */
    val ALL_LICENSE_FILENAMES = LICENSE_FILENAMES + PATENT_FILENAMES + ROOT_LICENSE_FILENAMES

    /**
     * Return glob patterns which match all files which may contain license information residing recursively within the
     * given absolute [directory] or in any of its ancestor directories.
     */
    fun getLicenseFileGlobsForDirectory(directory: String): List<String> =
        getFileGlobsForDirectoryAndAncestors(directory, ALL_LICENSE_FILENAMES)

    /**
     * Return a glob pattern which matches files in [directory], in any ancestor directory of [directory] and
     * [optionally][matchSubDirs] recursively in any sub-directory of directory, if the filename matches the
     * [filenamePattern].
     */
    internal fun getFileGlobForDirectory(
        directory: String,
        filenamePattern: String,
        matchSubDirs: Boolean
    ): String {
        val separator = "/".takeIf { !directory.endsWith("/") }.orEmpty()
        val globStar = "**/".takeIf { matchSubDirs }.orEmpty()

        val glob = "$directory$separator$globStar$filenamePattern"

        return if (glob.startsWith("/**/")) {
            // Simplify "/**/SUFFIX" to "**/SUFFIX":
            glob.substring(1)
        } else {
            glob
        }
    }

    /**
     * Return glob patterns which match files in [directory], in any ancestor directory of [directory] recursively
     * and in any sub-directory of directory, if the filename matches any of the [filenamePatterns].
     */
    internal fun getFileGlobsForDirectoryAndAncestors(
        directory: String,
        filenamePatterns: Collection<String>
    ): List<String> {
        val distinctPatterns = filenamePatterns.toSet()

        val patternsForDir = distinctPatterns.map {
            getFileGlobForDirectory(File(directory).invariantSeparatorsPath, it, true)
        }

        val patternsForAncestorDirs = getAllAncestorDirectories(directory).flatMap { dir ->
            distinctPatterns.map { getFileGlobForDirectory(dir, it, false) }
        }

        return (patternsForDir + patternsForAncestorDirs).sorted()
    }
}
