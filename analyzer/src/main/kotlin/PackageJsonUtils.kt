/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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

package com.here.ort.analyzer

import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode

import com.here.ort.model.readValue

import java.io.File
import java.nio.file.FileSystems
import java.nio.file.PathMatcher

internal class PackageJsonUtils {
    companion object {
        private val NPM_LOCK_FILES = listOf("npm-shrinkwrap.json", "package-lock.json")
        private val YARN_LOCK_FILES = listOf("yarn.lock")

        private data class DefinitionFileInfo(
                val definitionFile: File,
                val hasYarnLockfile: Boolean = false,
                val hasNpmLockfile: Boolean = false,
                val isYarnWorkspaceRoot: Boolean = false,
                val isYarnWorkspaceSubmodule: Boolean = false
        )

        fun hasNpmLockFile(directory: File) =
                NPM_LOCK_FILES.any { lockfile ->
                    File(directory, lockfile).isFile
                }

        fun hasYarnLockFile(directory: File) =
                YARN_LOCK_FILES.any { lockfile ->
                    File(directory, lockfile).isFile
                }

        fun mapDefinitionFilesForNpm(definitionFiles: Collection<File>) =
                getDefinitionFileInfo(definitionFiles.toSet()).filter { entry ->
                    !isHandledByYarn(entry)
                }.map { it.definitionFile }.toSet()

        fun mapDefinitionFilesForYarn(definitionFiles: Collection<File>) =
                getDefinitionFileInfo(definitionFiles.toSet()).filter { entry ->
                    isHandledByYarn(entry) && !entry.isYarnWorkspaceSubmodule
                }.map { it.definitionFile }.toSet()

        private fun isHandledByYarn(entry: DefinitionFileInfo) =
                entry.isYarnWorkspaceRoot || entry.isYarnWorkspaceSubmodule || entry.hasYarnLockfile

        private fun getDefinitionFileInfo(definitionFiles: Set<File>): Collection<DefinitionFileInfo> {
            val yarnWorkspaceSubmodules = getYarnWorkspaceSubmodules(definitionFiles)

            return definitionFiles.map { definitionFile ->
                DefinitionFileInfo(
                        definitionFile = definitionFile,
                        isYarnWorkspaceRoot = isYarnWorkspaceRoot(definitionFile),
                        hasYarnLockfile = hasYarnLockFile(definitionFile.parentFile),
                        hasNpmLockfile = hasNpmLockFile(definitionFile.parentFile),
                        isYarnWorkspaceSubmodule = yarnWorkspaceSubmodules.contains(definitionFile)
                )
            }
        }

        private fun isYarnWorkspaceRoot(definitionFile: File): Boolean {
            return definitionFile.readValue<ObjectNode>()["workspaces"] != null
        }

        private fun getYarnWorkspaceSubmodules(definitionFiles: Set<File>): Set<File> {
            val result = mutableSetOf<File>()

            definitionFiles.forEach { definitionFile ->
                val workspaceMatchers = getWorkspaceMatchers(definitionFile)
                workspaceMatchers.forEach { matcher ->
                    definitionFiles.forEach inner@ { other ->
                        // Since yarn workspaces matchers support '*' and '**' to match multiple directories the matcher
                        // cannot be used as is for matching the 'package.json' file. Thus matching against the project
                        // directory since this works out of the box. See also:
                        //   https://github.com/yarnpkg/yarn/issues/3986
                        //   https://github.com/yarnpkg/yarn/pull/5607
                        val projectDir = other.parentFile.toPath()
                        if (other != definitionFile && matcher.matches(projectDir)) {
                            result.add(other)
                            return@inner
                        }
                    }
                }
            }

            return result
        }

        private fun getWorkspaceMatchers(definitionFile: File): List<PathMatcher> {
            var workspaces = definitionFile.readValue<ObjectNode>()["workspaces"]
            if (workspaces != null && workspaces !is ArrayNode) {
                workspaces = workspaces["packages"]
            }

            return workspaces?.map {
                val pattern = "glob:${definitionFile.parentFile.invariantSeparatorsPath}/${it.textValue()}"
                FileSystems.getDefault().getPathMatcher(pattern)
            } ?: emptyList()
        }
    }
}
