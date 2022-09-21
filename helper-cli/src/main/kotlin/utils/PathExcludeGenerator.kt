/*
 * Copyright (C) 2022 EPAM Systems, Inc.
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

package org.ossreviewtoolkit.helper.utils

import java.io.File

import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.config.PathExcludeReason
import org.ossreviewtoolkit.model.config.PathExcludeReason.BUILD_TOOL_OF
import org.ossreviewtoolkit.model.config.PathExcludeReason.DOCUMENTATION_OF
import org.ossreviewtoolkit.model.config.PathExcludeReason.TEST_OF
import org.ossreviewtoolkit.utils.common.FileMatcher
import org.ossreviewtoolkit.utils.common.getCommonParentFile
import org.ossreviewtoolkit.utils.common.getDuplicates

/**
 * This class generates path excludes based on the set of file paths present in the source tree.
 */
internal object PathExcludeGenerator {
    /**
     * Return path excludes which likely but not necessarily apply to a source tree containing all given [filePaths]
     * which must be relative to the root directory of the source tree.
     */
    fun generatePathExcludes(filePaths: Collection<String>): Set<PathExclude> {
        val directoryExcludes = generateDirectoryExcludes(filePaths)
        val remainingFilePaths = filePaths.filterNot { filePath -> directoryExcludes.any { it.matches(filePath) } }
        val fileExcludes = generateFileExcludes(remainingFilePaths)

        return directoryExcludes + fileExcludes
    }

    /**
     * Return path excludes matching entire directories which likely but not necessarily apply to a source tree
     * containing all given [filePaths] which must be relative to the root directory of the source tree.
     */
    fun generateDirectoryExcludes(filePaths: Collection<String>): Set<PathExclude> {
        val files = filePaths.mapTo(mutableSetOf()) { File(it) }
        val dirs = getAllDirectories(files)

        val dirsToExclude = mutableMapOf<File, PathExcludeReason>()

        dirs.forEach { dir ->
            val (_, reason) = PATH_EXCLUDES_REASON_FOR_DIR_NAME.find { (pattern, _) ->
                FileMatcher.match(pattern, dir.name, ignoreCase = true)
            } ?: return@forEach

            dirsToExclude += dir to reason
        }

        val result = mutableSetOf<PathExclude>()

        dirsToExclude.forEach { (dir, reason) ->
            if (dir.getAncestorFiles().intersect(dirsToExclude.keys).isEmpty()) {
                result += PathExclude(
                    pattern = "${dir.invariantSeparatorsPath}/**",
                    reason = reason
                )
            }
        }

        return result
    }

    /**
     * Return filename specific path excludes which likely but not necessarily apply to a source tree containing all
     * given [filePaths] which must be relative to the root directory of the source tree.
     */
    private fun generateFileExcludes(filePaths: Collection<String>): Set<PathExclude> {
        val files = filePaths.mapTo(mutableSetOf()) { File(it) }
        val pathExcludes = mutableSetOf<PathExclude>()

        PATH_EXCLUDE_REASON_FOR_FILENAME.forEach { (pattern, reason) ->
            val patterns = createExcludePatterns(pattern, files)

            pathExcludes += patterns.map { PathExclude(it, reason) }
        }

        val filesForPathExcludes = pathExcludes.associateWith { pathExcludeExclude ->
            files.filter { pathExcludeExclude.matches(it.path) }.toSet()
        }

        return greedySetCover(filesForPathExcludes).toSet()
    }

    internal fun createExcludePatterns(filenamePattern: String, files: Set<File>): Set<String> {
        val matchingFiles = files.filter { FileMatcher.match(filenamePattern, it.name) }.takeIf { it.isNotEmpty() }
            ?: return emptySet()

        return createExcludePattern(
            directory = getCommonParentFile(matchingFiles).invariantSeparatorsPath,
            filenamePattern = if (matchingFiles.distinctBy { it.name }.size == 1) {
                matchingFiles.first().name
            } else {
                filenamePattern
            },
            matchSubdirectories = matchingFiles.distinctBy { it.parentFile ?: File("") }.size > 1
        ).let { setOf(it) }
    }

    private fun createExcludePattern(directory: String, filenamePattern: String, matchSubdirectories: Boolean): String {
        val dir = directory.takeIf { it.isEmpty() } ?: "$directory/"
        val wildcard = "**/".takeIf { matchSubdirectories }.orEmpty()
        return "$dir$wildcard$filenamePattern"
    }
}

private fun getAllDirectories(files: Collection<File>): Set<File> =
    files.flatMapTo(mutableSetOf()) { it.getAncestorFiles() }

/**
 * Return all ancestor directories ordered from parent to root.
 */
private fun File.getAncestorFiles(): List<File> {
    val result = mutableListOf<File>()

    var ancenstor = parentFile

    while (ancenstor != null) {
        result += ancenstor
        ancenstor = ancenstor.parentFile
    }

    return result
}

private fun <T> Collection<T>.checkNoDuplicatePatterns(keySelector: (T) -> String) =
    apply {
        val duplicatePatterns = getDuplicates(keySelector).keys

        require(duplicatePatterns.isEmpty()) {
            "Found duplicate patterns: ${duplicatePatterns.joinToString()}."
        }
    }

private val PATH_EXCLUDES_REASON_FOR_DIR_NAME = listOf(
    "*checkstyle*" to BUILD_TOOL_OF,
    "*conformance*" to BUILD_TOOL_OF,
    "*demo" to DOCUMENTATION_OF,
    "*demos" to DOCUMENTATION_OF,
    "*documentation*" to DOCUMENTATION_OF,
    "*example*" to DOCUMENTATION_OF,
    "*fixtures*" to TEST_OF,
    "*mock*" to BUILD_TOOL_OF,
    "*performance*" to BUILD_TOOL_OF,
    "*profiler*" to BUILD_TOOL_OF,
    "*test*" to TEST_OF,
    ".github" to BUILD_TOOL_OF,
    ".gradle" to BUILD_TOOL_OF,
    ".idea" to BUILD_TOOL_OF,
    ".mvn" to BUILD_TOOL_OF,
    ".teamcity" to BUILD_TOOL_OF,
    ".travis" to BUILD_TOOL_OF,
    "bench" to TEST_OF,
    "benches" to TEST_OF,
    "benchmark" to TEST_OF,
    "benchmarks" to TEST_OF,
    "build" to BUILD_TOOL_OF,
    "buildSrc" to BUILD_TOOL_OF,
    "ci" to BUILD_TOOL_OF,
    "cmake" to BUILD_TOOL_OF,
    "debug" to BUILD_TOOL_OF,
    "demo" to BUILD_TOOL_OF,
    "doc" to DOCUMENTATION_OF,
    "doc-files" to DOCUMENTATION_OF,
    "docs" to DOCUMENTATION_OF,
    "e2e" to TEST_OF,
    "javadoc" to DOCUMENTATION_OF,
    "jsdoc" to DOCUMENTATION_OF,
    "m4" to BUILD_TOOL_OF,
    "manual" to DOCUMENTATION_OF,
    "scripts" to BUILD_TOOL_OF,
    "spec" to DOCUMENTATION_OF,
    "srcm4" to BUILD_TOOL_OF,
    "tools" to BUILD_TOOL_OF,
    "tutorial" to DOCUMENTATION_OF,
    "winbuild" to BUILD_TOOL_OF,
).checkNoDuplicatePatterns { it.first }

private val PATH_EXCLUDE_REASON_FOR_FILENAME = listOf(
    "*.bazel" to BUILD_TOOL_OF,
    "*.cmake" to BUILD_TOOL_OF,
    "*.cmakein" to BUILD_TOOL_OF,
    "*.csproj" to BUILD_TOOL_OF,
    "*.gemspec" to BUILD_TOOL_OF,
    "*.mk" to BUILD_TOOL_OF,
    "*.podspec" to BUILD_TOOL_OF,
    "*.rake" to BUILD_TOOL_OF,
    "*_test.go" to TEST_OF,
    "CMakeLists.txt" to BUILD_TOOL_OF
).checkNoDuplicatePatterns { it.first }
