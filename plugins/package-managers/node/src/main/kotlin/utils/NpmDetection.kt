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

@file:Suppress("TooManyFunctions")

package org.ossreviewtoolkit.plugins.packagemanagers.node.utils

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.node.ArrayNode

import java.io.File
import java.lang.invoke.MethodHandles
import java.nio.file.FileSystems
import java.nio.file.PathMatcher

import org.apache.logging.log4j.kotlin.loggerOf

import org.ossreviewtoolkit.model.readTree
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.plugins.packagemanagers.node.Yarn2
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.ort.showStackTrace

private val logger = loggerOf(MethodHandles.lookup().lookupClass())

/**
 * Return whether the [directory] contains an NPM lock file.
 */
internal fun hasNpmLockFile(directory: File) =
    NPM_LOCK_FILES.any { lockfile ->
        File(directory, lockfile).isFile
    }

private val NPM_LOCK_FILES = listOf("npm-shrinkwrap.json", "package-lock.json")

/**
 * Return whether the [directory] contains a PNPM lock file.
 */
internal fun hasPnpmLockFile(directory: File) =
    PNPM_LOCK_FILES.any { lockfile ->
        File(directory, lockfile).isFile
    }

private val PNPM_LOCK_FILES = listOf("pnpm-lock.yaml")

/**
 * Return whether the [directory] contains a Yarn lock file.
 */
internal fun hasYarnLockFile(directory: File) =
    YARN_LOCK_FILES.any { lockfile ->
        File(directory, lockfile).isFile
    }

private val YARN_LOCK_FILES = listOf("yarn.lock")

/**
 * Return whether the [directory] contains a Yarn resource file in YAML format, specific to Yarn 2+.
 * Yarn1 has a non-YAML `.yarnrc` configuration file.
 */
internal fun hasYarn2ResourceFile(directory: File) = directory.resolve(Yarn2.YARN2_RESOURCE_FILE).isFile

/**
 * Map [definitionFiles] to contain only files handled by NPM.
 */
internal fun mapDefinitionFilesForNpm(definitionFiles: Collection<File>): Set<File> =
    getPackageJsonInfo(definitionFiles.toSet())
        .filter { !it.isHandledByYarn && !it.isHandledByPnpm }
        .mapTo(mutableSetOf()) { it.definitionFile }

/**
 * Map [definitionFiles] to contain only files handled by PNPM.
 */
internal fun mapDefinitionFilesForPnpm(definitionFiles: Collection<File>): Set<File> =
    getPackageJsonInfo(definitionFiles.toSet())
        .filter { it.isHandledByPnpm && !it.isPnpmWorkspaceSubmodule }
        .mapTo(mutableSetOf()) { it.definitionFile }

/**
 * Map [definitionFiles] to contain only files handled by Yarn.
 */
internal fun mapDefinitionFilesForYarn(definitionFiles: Collection<File>): Set<File> =
    getPackageJsonInfo(definitionFiles.toSet())
        .filter { it.isHandledByYarn && !it.isYarnWorkspaceSubmodule && !it.hasYarn2ResourceFile }
        .mapTo(mutableSetOf()) { it.definitionFile }

/**
 * Map [definitionFiles] to contain only files handled by Yarn 2+.
 */
internal fun mapDefinitionFilesForYarn2(definitionFiles: Collection<File>): Set<File> =
    getPackageJsonInfo(definitionFiles.toSet())
        .filter { it.isHandledByYarn && !it.isYarnWorkspaceSubmodule && it.hasYarn2ResourceFile }
        .mapTo(mutableSetOf()) { it.definitionFile }

private data class PackageJsonInfo(
    val definitionFile: File,
    val hasYarnLockfile: Boolean = false,
    val hasYarn2ResourceFile: Boolean = false,
    val hasNpmLockfile: Boolean = false,
    val hasPnpmLockfile: Boolean = false,
    val isPnpmWorkspaceRoot: Boolean = false,
    val isPnpmWorkspaceSubmodule: Boolean = false,
    val isYarnWorkspaceRoot: Boolean = false,
    val isYarnWorkspaceSubmodule: Boolean = false
) {
    val isHandledByPnpm = isPnpmWorkspaceRoot || isPnpmWorkspaceSubmodule || hasPnpmLockfile
    val isHandledByYarn = isYarnWorkspaceRoot || isYarnWorkspaceSubmodule || hasYarnLockfile
}

private fun getPackageJsonInfo(definitionFiles: Set<File>): Collection<PackageJsonInfo> {
    fun isPnpmWorkspaceRoot(directory: File) = directory.resolve("pnpm-workspace.yaml").isFile

    fun isYarnWorkspaceRoot(definitionFile: File) =
        try {
            definitionFile.readTree().has("workspaces")
        } catch (e: JsonProcessingException) {
            e.showStackTrace()

            logger.error {
                "Could not parse '${definitionFile.invariantSeparatorsPath}': ${e.collectMessages()}"
            }

            false
        }

    val pnpmWorkspaceSubmodules = getPnpmWorkspaceSubmodules(definitionFiles)
    val yarnWorkspaceSubmodules = getYarnWorkspaceSubmodules(definitionFiles)

    return definitionFiles.map { definitionFile ->
        PackageJsonInfo(
            definitionFile = definitionFile,
            isPnpmWorkspaceRoot = isPnpmWorkspaceRoot(definitionFile.parentFile),
            isYarnWorkspaceRoot = isYarnWorkspaceRoot(definitionFile) &&
                !isPnpmWorkspaceRoot(definitionFile.parentFile),
            hasYarnLockfile = hasYarnLockFile(definitionFile.parentFile),
            hasNpmLockfile = hasNpmLockFile(definitionFile.parentFile),
            hasPnpmLockfile = hasPnpmLockFile(definitionFile.parentFile),
            hasYarn2ResourceFile = hasYarn2ResourceFile(definitionFile.parentFile),
            isPnpmWorkspaceSubmodule = definitionFile in pnpmWorkspaceSubmodules,
            isYarnWorkspaceSubmodule = definitionFile in yarnWorkspaceSubmodules &&
                definitionFile !in pnpmWorkspaceSubmodules
        )
    }
}

private fun getPnpmWorkspaceMatchers(definitionFile: File): List<PathMatcher> {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class PnpmWorkspaces(val packages: List<String> = emptyList())

    fun String.isComment() = trim().startsWith("#")

    val pnpmWorkspaceFile = definitionFile.resolveSibling("pnpm-workspace.yaml")

    return if (pnpmWorkspaceFile.isFile && pnpmWorkspaceFile.readLines().any { !it.isComment() }) {
        val workspaceMatchers = pnpmWorkspaceFile.readValue<PnpmWorkspaces>().packages

        workspaceMatchers.map { matcher ->
            val pattern = "glob:${definitionFile.parentFile.invariantSeparatorsPath}/${matcher.removeSuffix("/")}"
            FileSystems.getDefault().getPathMatcher(pattern)
        }
    } else {
        // Empty "pnpm-workspace.yaml" files can be used for regular projects within a workspace setup, see
        // https://github.com/pnpm/pnpm/issues/2412.
        emptyList()
    }
}

private fun getPnpmWorkspaceSubmodules(definitionFiles: Set<File>): Set<File> {
    val result = mutableSetOf<File>()

    definitionFiles.forEach { definitionFile ->
        val pnpmWorkspacesFile = definitionFile.resolveSibling("pnpm-workspace.yaml")

        if (pnpmWorkspacesFile.isFile) {
            val pathMatchers = getPnpmWorkspaceMatchers(definitionFile)

            pathMatchers.forEach { matcher ->
                definitionFiles.forEach inner@{ other ->
                    val projectDir = other.parentFile.toPath()
                    if (other != definitionFile && matcher.matches(projectDir)) {
                        result += other
                        return@inner
                    }
                }
            }
        } else {
            return@forEach
        }
    }

    return result
}

private fun getYarnWorkspaceSubmodules(definitionFiles: Set<File>): Set<File> {
    fun getWorkspaceMatchers(definitionFile: File): List<PathMatcher> {
        var workspaces = try {
            definitionFile.readTree().get("workspaces")
        } catch (e: JsonProcessingException) {
            e.showStackTrace()

            logger.error { "Could not parse '${definitionFile.invariantSeparatorsPath}': ${e.collectMessages()}" }

            null
        }

        if (workspaces != null && workspaces !is ArrayNode) {
            workspaces = workspaces["packages"]
        }

        return workspaces?.map {
            val pattern = "glob:${definitionFile.parentFile.invariantSeparatorsPath}/${it.textValue()}"
            FileSystems.getDefault().getPathMatcher(pattern)
        }.orEmpty()
    }

    val result = mutableSetOf<File>()

    definitionFiles.forEach { definitionFile ->
        val workspaceMatchers = getWorkspaceMatchers(definitionFile)
        workspaceMatchers.forEach { matcher ->
            definitionFiles.forEach inner@{ other ->
                // Since yarn workspaces matchers support '*' and '**' to match multiple directories the matcher
                // cannot be used as is for matching the 'package.json' file. Thus matching against the project
                // directory since this works out of the box. See also:
                //   https://github.com/yarnpkg/yarn/issues/3986
                //   https://github.com/yarnpkg/yarn/pull/5607
                val projectDir = other.parentFile.toPath()
                if (other != definitionFile && matcher.matches(projectDir)) {
                    result += other
                    return@inner
                }
            }
        }
    }

    return result
}
