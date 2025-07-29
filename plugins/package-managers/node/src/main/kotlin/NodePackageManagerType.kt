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

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.model.readTree
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.div

/**
 * An enum of all supported Node package managers.
 */
enum class NodePackageManagerType(
    internal val projectType: String,
    internal val lockfileName: String,
    internal val markerFileName: String? = null,
    internal val workspaceFileName: String = NodePackageManagerType.DEFINITION_FILE
) {
    NPM(
        projectType = "NPM",
        lockfileName = "package-lock.json", // See https://docs.npmjs.com/cli/v7/configuring-npm/package-lock-json.
        markerFileName = "npm-shrinkwrap.json" // See https://docs.npmjs.com/cli/v6/configuring-npm/shrinkwrap-json.
    ) {
        override fun hasLockfile(projectDir: File): Boolean =
            super.hasLockfile(projectDir) || hasNonEmptyFile(projectDir, markerFileName)
    },

    PNPM(
        projectType = "PNPM",
        lockfileName = "pnpm-lock.yaml", // See https://pnpm.io/git#lockfiles.
        workspaceFileName = "pnpm-workspace.yaml"
    ) {
        override fun getWorkspaces(projectDir: File): List<String>? {
            val workspaceFile = projectDir / workspaceFileName
            if (!workspaceFile.isFile) return null

            val packages = runCatching {
                workspaceFile.readTree().get("packages")
            }.onFailure {
                logger.error { "Failed to parse '$workspaceFile': ${it.collectMessages()}" }
            }.getOrNull() ?: return null

            return packages.map { "${workspaceFile.parentFile.invariantSeparatorsPath}/${it.textValue()}" }
        }
    },

    YARN(
        projectType = "Yarn",
        lockfileName = "yarn.lock" // See https://classic.yarnpkg.com/en/docs/yarn-lock.
    ) {
        private val lockfileMarker = "# yarn lockfile v1"

        override fun hasLockfile(projectDir: File): Boolean {
            val lockfile = projectDir / lockfileName
            if (!lockfile.isFile) return false

            return lockfile.useLines { lines ->
                lines.take(2).lastOrNull() == lockfileMarker
            }
        }
    },

    YARN2(
        projectType = "Yarn2",
        lockfileName = "yarn.lock", // See https://classic.yarnpkg.com/en/docs/yarn-lock.
        markerFileName = ".yarnrc.yml"
    ) {
        private val lockfileMarker = "__metadata:"

        override fun hasLockfile(projectDir: File): Boolean {
            val lockfile = projectDir.resolve(lockfileName)
            if (!lockfile.isFile) return false

            return lockfile.useLines { lines ->
                lines.take(4).lastOrNull() == lockfileMarker
            }
        }
    };

    companion object {
        /**
         * The name of the definition file used by all Node package managers.
         */
        const val DEFINITION_FILE = "package.json"

        /**
         * A regular expression to find an asterisk that is not surrounded by another asterisk.
         */
        private val WORKSPACES_SINGLE_ASTERISK_REGEX = Regex("(?<!\\*)\\*(?!\\*)")

        /**
         * Return the set of package managers that are most likely responsible for the given [projectDir].
         */
        fun forDirectory(projectDir: File): Set<NodePackageManagerType> {
            val scores = NodePackageManagerType.entries.associateWith {
                it.getFileScore(projectDir)
            }

            // Get the overall maximum score.
            val maxScore = scores.maxBy { it.value }

            // Get all package managers with the maximum score.
            return scores.filter { it.value == maxScore.value }.keys
        }
    }

    /**
     * Return true if the [projectDir] contains a lockfile for this package manager, or return false otherwise.
     */
    internal open fun hasLockfile(projectDir: File): Boolean = hasNonEmptyFile(projectDir, lockfileName)

    /**
     * If the [projectDir] contains a workspace file for this package manager, return the list of package patterns, or
     * return null otherwise.
     */
    internal open fun getWorkspaces(projectDir: File): List<String>? {
        val workspaceFile = projectDir.resolve(workspaceFileName)
        if (!workspaceFile.isFile) return null

        val workspaces = runCatching {
            workspaceFile.readTree().get("workspaces")
        }.onFailure {
            logger.error { "Failed to parse '$workspaceFile': ${it.collectMessages()}" }
        }.getOrNull() ?: return null

        val packages = when {
            workspaces.isArray -> workspaces
            workspaces.isObject -> workspaces["packages"]
            else -> null
        } ?: run {
            logger.warn { "Unable to read workspaces from '$workspaceFile'." }
            return null
        }

        return packages.map {
            val pattern = "${workspaceFile.parentFile.invariantSeparatorsPath}/${it.textValue()}"

            // NPM and Yarn treat "*" as an alias for "**", so replace any single "*" with "**".
            pattern.replace(WORKSPACES_SINGLE_ASTERISK_REGEX, "**")
        }
    }

    /**
     * Return a score for the [projectDir] based on the presence of files specific to this package manager.
     * The higher the score, the more likely it is that the [projectDir] is managed by this package manager.
     */
    internal fun getFileScore(projectDir: File): Int =
        listOf(
            hasLockfile(projectDir),
            hasNonEmptyFile(projectDir, markerFileName),
            // Only count the presence of an additional workspace file if it is not the definition file.
            workspaceFileName != DEFINITION_FILE && hasNonEmptyFile(projectDir, workspaceFileName)
        ).count { it }
}

/**
 * Return true if [fileName] is not null and the [projectDir] contains a non-empty file named [fileName], or return
 * false otherwise.
 */
private fun hasNonEmptyFile(projectDir: File, fileName: String?): Boolean =
    fileName != null && projectDir.resolve(fileName).let { it.isFile && it.length() > 0 }
