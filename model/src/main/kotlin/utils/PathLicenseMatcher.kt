/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.model.utils

import java.io.File

import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.config.LicenseFilePatterns
import org.ossreviewtoolkit.utils.common.FileMatcher
import org.ossreviewtoolkit.utils.common.getAllAncestorDirectories

/**
 * A heuristic for determining which (root) license files apply to any file or directory.
 *
 * For any given directory the heuristic tries to assign license files by utilizing
 * [LicenseFilePatterns.licenseFilenames] and patent files by utilizing [LicenseFilePatterns.patentFilenames]
 * independently of one another. The [LicenseFilePatterns.otherLicenseFilenames] serve only as fallback to find
 * license files if there isn't any match for [LicenseFilePatterns.licenseFilenames].
 *
 * To determine the (root) license files applicable for a specific directory, all filenames in that directory are
 * matched against [LicenseFilePatterns.licenseFilenames]. If there are matches then these are used as result,
 * otherwise that search is repeated recursively in the parent directory. If there is no parent directory (because the
 * root was already searched but no result was found) then start from scratch using the fallback pattern
 * [LicenseFilePatterns.otherLicenseFilenames].
 *
 * Patent files are assigned in an analog way, but without any fallback pattern.
 */
class PathLicenseMatcher(licenseFilePatterns: LicenseFilePatterns = LicenseFilePatterns.DEFAULT) {
    private val licenseFileMatcher = createFileMatcher(licenseFilePatterns.licenseFilenames)
    private val patentFileMatcher = createFileMatcher(licenseFilePatterns.patentFilenames)
    private val otherLicenseFileMatcher = createFileMatcher(licenseFilePatterns.otherLicenseFilenames)

    /**
     * Return a mapping from the given relative [directories] to the licenses findings for the (root) license files
     * applicable to the respective directory. The values of the map entries are subsets of the given
     * [licenseFindings].
     */
    fun getApplicableLicenseFindingsForDirectories(
        licenseFindings: Collection<LicenseFinding>,
        directories: Collection<String>
    ): Map<String, Set<LicenseFinding>> {
        val licenseFindingsByPath = licenseFindings.groupBy { it.location.path }

        return getApplicableLicenseFilesForDirectories(
            licenseFindingsByPath.keys, directories
        ).mapValues { (_, licenseFilePath) ->
            licenseFilePath.flatMapTo(mutableSetOf()) { licenseFindingsByPath.getValue(it) }
        }
    }

    /**
     * Return a mapping from the given relative [directories] to the relative paths of the (root) licenses files
     * applicable to that respective directory. The values of the map entries are subsets of the given
     * [relativeFilePaths].
     */
    fun getApplicableLicenseFilesForDirectories(
        relativeFilePaths: Collection<String>,
        directories: Collection<String>
    ): Map<String, Set<String>> {
        require(directories.none { it.startsWith("/") })
        require(relativeFilePaths.none { it.startsWith("/") })

        fun filePathsByDir(matcher: FileMatcher): Map<String, Set<String>> =
            relativeFilePaths.filter { matcher.matches("/$it") }.groupBy {
                File("/$it").parentFile.invariantSeparatorsPath
            }.mapValues { it.value.toSet() }

        val licenseFiles = filePathsByDir(licenseFileMatcher)
        val patentFiles = filePathsByDir(patentFileMatcher)
        val otherLicenseFiles = filePathsByDir(otherLicenseFileMatcher)

        val result = mutableMapOf<String, MutableSet<String>>()

        directories.map { "/$it" }.forEach { directory ->
            val directoriesOnPathToRoot = listOf(directory) + getAllAncestorDirectories(directory)
            val licenseFilesForDirectory = result.getOrPut(directory) { mutableSetOf() }

            fun addApplicableLicenseFiles(licenseFilesByDir: Map<String, Collection<String>>) {
                directoriesOnPathToRoot.forEach { currentDir ->
                    licenseFilesByDir[currentDir]?.let {
                        licenseFilesForDirectory += it
                        return
                    }
                }
            }

            addApplicableLicenseFiles(licenseFiles)

            if (licenseFilesForDirectory.isEmpty()) {
                addApplicableLicenseFiles(otherLicenseFiles)
            }

            addApplicableLicenseFiles(patentFiles)
        }

        return result.mapKeys { it.key.removePrefix("/") }
    }
}

private fun createFileMatcher(filenamePatterns: Collection<String>): FileMatcher =
    FileMatcher(filenamePatterns.map { "/**/$it" }, ignoreCase = true)
