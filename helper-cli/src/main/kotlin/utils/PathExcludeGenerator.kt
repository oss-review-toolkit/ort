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

/**
 * This class generates path excludes based on the set of file paths present in the source tree.
 */
internal object PathExcludeGenerator {
    /**
     * Return path excludes which likely but not necessarily apply to a source tree containing all given [filePaths]
     * which must be relative to the root directory of the source tree.
     */
    fun generatePathExcludes(filePaths: Collection<String>): List<PathExclude> {
        val files = filePaths.mapTo(mutableSetOf()) { File(it) }
        val dirs = getAllDirs(files)

        val dirsToExclude = mutableMapOf<File, PathExcludeReason>()

        dirs.forEach { dir ->
            val (_, reason) = PATH_EXCLUDES_REASON_FOR_DIR_NAME.entries.find { (pattern, _) ->
                FileMatcher.match(pattern, dir.name, ignoreCase = true)
            } ?: return@forEach

            dirsToExclude += dir to reason
        }

        val result = mutableSetOf<PathExclude>()

        dirsToExclude.forEach { (dir, reason) ->
            if (dir.getAncestorFiles().intersect(dirsToExclude.keys).isEmpty()) {
                result += PathExclude(
                      pattern = "${dir.path}/**",
                      reason = reason
                )
            }
        }

        return result.toList()
    }
}

private fun getAllDirs(files: Set<File>): Set<File> =
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

private val PATH_EXCLUDES_REASON_FOR_DIR_NAME = mapOf(
    "*demo" to DOCUMENTATION_OF,
    "*demos" to DOCUMENTATION_OF,
    "*example*" to DOCUMENTATION_OF,
    "*test*" to TEST_OF,
    ".github" to BUILD_TOOL_OF,
    ".gradle" to BUILD_TOOL_OF,
    ".idea" to BUILD_TOOL_OF,
    ".mvn" to BUILD_TOOL_OF,
    ".travis" to BUILD_TOOL_OF,
    "bench" to TEST_OF,
    "benches" to TEST_OF,
    "benchmark" to TEST_OF,
    "benchmarks" to TEST_OF,
    "build" to BUILD_TOOL_OF,
    "cmake" to BUILD_TOOL_OF,
    "doc" to DOCUMENTATION_OF,
    "doc-files" to DOCUMENTATION_OF,
    "docs" to DOCUMENTATION_OF,
    "javadoc" to DOCUMENTATION_OF,
    "m4" to BUILD_TOOL_OF,
    "scripts" to BUILD_TOOL_OF,
    "tools" to BUILD_TOOL_OF,
    "tutorial" to DOCUMENTATION_OF,
    "winbuild" to BUILD_TOOL_OF
)
