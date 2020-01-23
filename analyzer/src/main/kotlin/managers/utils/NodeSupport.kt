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

@file:Suppress("TooManyFunctions")

package com.here.ort.analyzer.managers.utils

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode

import com.here.ort.model.readValue
import com.here.ort.utils.collectMessagesAsString
import com.here.ort.utils.hasRevisionFragment
import com.here.ort.utils.log
import com.here.ort.utils.showStackTrace

import java.io.File
import java.net.URI
import java.net.URISyntaxException
import java.nio.file.FileSystems
import java.nio.file.PathMatcher

/**
 * Return whether the [directory] contains an NPM lock file.
 */
fun hasNpmLockFile(directory: File) =
    NPM_LOCK_FILES.any { lockfile ->
        File(directory, lockfile).isFile
    }

/**
 * Return a Boolean indicating whether the [directory] contains a PNPM lock file.
 */
fun hasPnpmLockFile(directory: File) =
    PNPM_LOCK_FILES.any { lockfile ->
        File(directory, lockfile).isFile
    }

/**
 * Return whether the [directory] contains a Yarn lock file.
 */
fun hasYarnLockFile(directory: File) =
    YARN_LOCK_FILES.any { lockfile ->
        File(directory, lockfile).isFile
    }

/**
 * Map [definitionFiles] to contain only files handled by NPM.
 */
fun mapDefinitionFilesForNpm(definitionFiles: Collection<File>): Set<File> =
    getPackageJsonInfo(definitionFiles.toSet()).filter { entry ->
        !isHandledByYarn(entry) && !entry.hasPnpmLockfile
    }.mapTo(mutableSetOf()) { it.definitionFile }

/**
 * Map [definitionFiles] to contain only files handled by PNPM.
 */
fun mapDefinitionFilesForPnpm(definitionFiles: Collection<File>): Set<File> =
    getPackageJsonInfo(definitionFiles.toSet()).filter { entry ->
        entry.hasPnpmLockfile
    }.mapTo(mutableSetOf()) { it.definitionFile }

/**
 * Map [definitionFiles] to contain only files handled by Yarn.
 */
fun mapDefinitionFilesForYarn(definitionFiles: Collection<File>): Set<File> =
    getPackageJsonInfo(definitionFiles.toSet()).filter { entry ->
        isHandledByYarn(entry) && !entry.isYarnWorkspaceSubmodule && !entry.hasPnpmLockfile
    }.mapTo(mutableSetOf()) { it.definitionFile }

/**
 * Expand NPM shortcuts in [url] that refer to hosting sites to full URLs so that they can be used in a regular way.
 */
fun expandNpmShortcutURL(url: String): String {
    // A hierarchical URI looks like
    //     [scheme:][//authority][path][?query][#fragment]
    // where a server-based "authority" has the syntax
    //     [user-info@]host[:port]
    val uri = try {
        // At this point we do not know whether the URL is actually valid, so use the more general URI.
        URI(url)
    } catch (e: URISyntaxException) {
        // Fall back to returning the original URL.
        return url
    }

    val path = uri.schemeSpecificPart

    // Do not mess with crazy URLs.
    if (path.startsWith("git@") || path.startsWith("github.com") || path.startsWith("gitlab.com")) return url

    return if (!path.isNullOrEmpty() && listOf(uri.authority, uri.query).all { it == null }) {
        // See https://docs.npmjs.com/files/package.json#github-urls.
        val revision = if (uri.hasRevisionFragment()) "#${uri.fragment}" else ""

        // See https://docs.npmjs.com/files/package.json#repository.
        when (uri.scheme) {
            null, "github" -> "https://github.com/$path.git$revision"
            "gist" -> "https://gist.github.com/$path$revision"
            "bitbucket" -> "https://bitbucket.org/$path.git$revision"
            "gitlab" -> "https://gitlab.com/$path.git$revision"
            else -> url
        }
    } else {
        url
    }
}

/**
 * Extract any proxy URL from [NPM configuration][npmRc], return null if no proxy URL is configured.
 */
fun readProxySettingFromNpmRc(npmRc: String): String? {
    var proxyUrl: String? = null

    npmRc.lines().forEach { line ->
        val keyAndValue = line.split('=', limit = 2).map { it.trim() }
        if (keyAndValue.size != 2) return@forEach

        val (key, value) = keyAndValue
        if (key != "proxy" && key != "https-proxy") return@forEach

        if (value.matches(HTTP_REGEX)) {
            proxyUrl = value
        } else if (value.isNotBlank() && value != "null") {
            // Note that even HTTPS proxies use "http://" as the protocol!
            proxyUrl = "http://$value"
        }
    }

    return proxyUrl
}

private val HTTP_REGEX = Regex("^https?://.+$")

private val NPM_LOCK_FILES = listOf("npm-shrinkwrap.json", "package-lock.json")
private val PNPM_LOCK_FILES = listOf("pnpm-lock.yaml")
private val YARN_LOCK_FILES = listOf("yarn.lock")

private data class PackageJsonInfo(
    val definitionFile: File,
    val hasYarnLockfile: Boolean = false,
    val hasNpmLockfile: Boolean = false,
    val hasPnpmLockfile: Boolean = false,
    val isYarnWorkspaceRoot: Boolean = false,
    val isYarnWorkspaceSubmodule: Boolean = false
)

private fun isHandledByYarn(entry: PackageJsonInfo) =
    entry.isYarnWorkspaceRoot || entry.isYarnWorkspaceSubmodule || entry.hasYarnLockfile

private fun getPackageJsonInfo(definitionFiles: Set<File>): Collection<PackageJsonInfo> {
    val yarnWorkspaceSubmodules = getYarnWorkspaceSubmodules(definitionFiles)

    return definitionFiles.map { definitionFile ->
        PackageJsonInfo(
            definitionFile = definitionFile,
            isYarnWorkspaceRoot = isYarnWorkspaceRoot(definitionFile),
            hasYarnLockfile = hasYarnLockFile(definitionFile.parentFile),
            hasNpmLockfile = hasNpmLockFile(definitionFile.parentFile),
            hasPnpmLockfile = hasPnpmLockFile(definitionFile.parentFile),
            isYarnWorkspaceSubmodule = yarnWorkspaceSubmodules.contains(definitionFile)
        )
    }
}

private fun isYarnWorkspaceRoot(definitionFile: File) =
    try {
        definitionFile.readValue<ObjectNode>()["workspaces"] != null
    } catch (e: JsonProcessingException) {
        e.showStackTrace()

        e.log.error { "Could not parse '${definitionFile.invariantSeparatorsPath}': ${e.collectMessagesAsString()}" }

        false
    }

private fun getYarnWorkspaceSubmodules(definitionFiles: Set<File>): Set<File> {
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
                    result.add(other)
                    return@inner
                }
            }
        }
    }

    return result
}

private fun getWorkspaceMatchers(definitionFile: File): List<PathMatcher> {
    var workspaces = try {
        definitionFile.readValue<ObjectNode>()["workspaces"]
    } catch (e: JsonProcessingException) {
        e.showStackTrace()

        e.log.error { "Could not parse '${definitionFile.invariantSeparatorsPath}': ${e.collectMessagesAsString()}" }

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
