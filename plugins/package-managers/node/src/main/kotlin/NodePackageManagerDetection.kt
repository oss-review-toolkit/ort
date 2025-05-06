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

package org.ossreviewtoolkit.plugins.packagemanagers.node

import java.io.File
import java.nio.file.FileSystems
import java.nio.file.PathMatcher

import org.apache.logging.log4j.kotlin.logger

/**
 * A class to detect the package managers used for the give [definitionFiles].
 */
internal class NodePackageManagerDetection(private val definitionFiles: Collection<File>) {
    /**
     * A map of project directories to the set of package managers that are most likely responsible for the project. If
     * the set is empty, none of the package managers is responsible.
     */
    private val managerTypesForProjectDir: Map<File, Set<NodePackageManagerType>> by lazy {
        definitionFiles.associate { file ->
            val projectDir = file.parentFile
            projectDir to NodePackageManagerType.forDirectory(projectDir)
        }
    }

    /**
     * A map of project directories to the set of workspace patterns for the project. If a project directory does not
     * define a workspace, the list of patterns is empty.
     */
    private val workspacePatternsForProjectDir: Map<File, List<PathMatcher>> by lazy {
        definitionFiles.associate { file ->
            val projectDir = file.parentFile
            val patterns = NodePackageManagerType.entries.mapNotNull { it.getWorkspaces(projectDir) }.flatten()
            projectDir to patterns.map {
                FileSystems.getDefault().getPathMatcher("glob:${it.removeSuffix("/")}")
            }
        }
    }

    /**
     * Return the roots of the workspace that [projectDir] is part of. It could be multiple roots if multiple package
     * managers define overlapping workspaces. If [projectDir] does not belong to any workspace, the empty set is
     * returned.
     */
    private fun getWorkspaceRoots(projectDir: File): Set<File> =
        workspacePatternsForProjectDir.filter { (_, patterns) ->
            patterns.any { it.matches(projectDir.toPath()) }
        }.keys

    /**
     * Return whether the given [manager] is applicable for then given [definitionFile], or return null if unknown.
     */
    fun isApplicable(manager: NodePackageManagerType, definitionFile: File): Boolean? {
        val projectDir = definitionFile.parentFile

        // Try to clearly determine the package manager from files specific to it.
        val managersFromFiles = managerTypesForProjectDir[projectDir].orEmpty()
        when {
            manager !in managersFromFiles -> return false
            managersFromFiles.size == 1 -> {
                logger.info { "Detected '$definitionFile' to be the root of a(n) $manager project." }
                return true
            }
        }

        // There is ambiguity when only looking at the files, so also look at any workspaces to clearly determine
        // the package manager.
        val managersFromWorkspaces = getWorkspaceRoots(projectDir).mapNotNull {
            managerTypesForProjectDir[it]
        }.flatten()

        if (managersFromWorkspaces.isNotEmpty()) {
            logger.info {
                "Skipping '$definitionFile' as it is part of a workspace implicitly handled by $managersFromWorkspaces."
            }

            return false
        }

        return null
    }

    /**
     * Return those [definitionFiles] that define root projects for the given [manager], or that are managed by
     * [fallbackType] as a fallback.
     */
    fun filterApplicable(
        manager: NodePackageManagerType,
        fallbackType: NodePackageManagerType = NodePackageManagerType.NPM
    ): List<File> =
        definitionFiles.filter { file ->
            val projectDir = file.parentFile
            val managersFromFiles = managerTypesForProjectDir[projectDir].orEmpty()

            isApplicable(manager, file) ?: (manager == fallbackType).also {
                if (it) {
                    logger.warn {
                        "Any of $managersFromFiles could be the package manager for '$file'. " +
                            "Assuming it is a(n) $fallbackType project."
                    }
                }
            }
        }
}
