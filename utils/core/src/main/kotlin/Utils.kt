/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.utils.core

import java.io.File

import kotlin.reflect.full.memberProperties

private val mavenCentralUrlPattern = Regex("^https?://repo1?\\.maven(\\.apache)?\\.org(/.*)?$")

/**
 * The directory to store ORT (read-only) configuration in.
 */
val ortConfigDirectory by lazy {
    Os.env[ORT_CONFIG_DIR_ENV_NAME]?.takeUnless {
        it.isEmpty()
    }?.let {
        File(it)
    } ?: ortDataDirectory.resolve("config")
}

/**
 * The directory to store ORT (read-write) tools in.
 */
val ortToolsDirectory by lazy {
    Os.env[ORT_TOOLS_DIR_ENV_NAME]?.takeUnless {
        it.isEmpty()
    }?.let {
        File(it)
    } ?: ortDataDirectory.resolve("tools")
}

/**
 * The directory to store ORT (read-write) data in, like caches and archives.
 */
val ortDataDirectory by lazy {
    Os.env[ORT_DATA_DIR_ENV_NAME]?.takeUnless {
        it.isEmpty()
    }?.let {
        File(it)
    } ?: Os.userHomeDirectory.resolve(".ort")
}

/**
 * Global variable that gets toggled by a command line parameter parsed in the main entry points of the modules.
 */
var printStackTrace = false

/**
 * Return whether [T] (usually an instance of a data class) has any non-null property.
 */
inline fun <reified T : Any> T.hasNonNullProperty() =
    T::class.memberProperties.asSequence().map { it.get(this) }.any { it != null }

/**
 * Filter a list of [names] to include only those that likely belong to the given [version] of an optional [project].
 */
fun filterVersionNames(version: String, names: List<String>, project: String? = null): List<String> {
    if (version.isBlank() || names.isEmpty()) return emptyList()

    // If there are full matches, return them right away.
    val fullMatches = names.filter { it.equals(version, ignoreCase = true) }
    if (fullMatches.isNotEmpty()) return fullMatches

    // The list of supported version separators.
    val versionSeparators = listOf('-', '_', '.')
    val versionHasSeparator = versionSeparators.any { version.contains(it) }

    // Create variants of the version string to recognize.
    data class VersionVariant(val name: String, val separators: List<Char>)

    val versionLower = version.lowercase()
    val versionVariants = mutableListOf(VersionVariant(versionLower, versionSeparators))

    val separatorRegex = Regex(versionSeparators.joinToString("", "[", "]"))
    versionSeparators.mapTo(versionVariants) {
        VersionVariant(versionLower.replace(separatorRegex, it.toString()), listOf(it))
    }

    val filteredNames = names.filter {
        val name = it.lowercase()

        versionVariants.any { versionVariant ->
            // Allow to ignore suffixes in names that are separated by something else than the current separator, e.g.
            // for version "3.3.1" accept "3.3.1-npm-packages" but not "3.3.1.0".
            val hasIgnorableSuffix = name.withoutPrefix(versionVariant.name)?.let { tail ->
                tail.firstOrNull() !in versionVariant.separators
            } ?: false

            // Allow to ignore prefixes in names that are separated by something else than the current separator, e.g.
            // for version "0.10" accept "docutils-0.10" but not "1.0.10".
            val hasIgnorablePrefix = name.withoutSuffix(versionVariant.name)?.let { head ->
                val last = head.lastOrNull()
                val forelast = head.dropLast(1).lastOrNull()

                val currentSeparators = if (versionHasSeparator) versionVariant.separators else versionSeparators

                // Full match with the current version variant.
                last == null
                        // The prefix does not end with the current separators or a digit.
                        || (last !in currentSeparators && !last.isDigit())
                        // The prefix ends with the current separators but the forelast character is not a digit.
                        || (last in currentSeparators && (forelast == null || !forelast.isDigit()))
                        // The prefix ends with 'v' and the forelast character is a separator.
                        || (last == 'v' && (forelast == null || forelast in currentSeparators))
            } ?: false

            hasIgnorableSuffix || hasIgnorablePrefix
        }
    }

    return filteredNames.filter {
        // startsWith("") returns "true" for any string, so we get an unfiltered list if "project" is "null".
        it.startsWith(project.orEmpty())
    }.let {
        // Fall back to the original list if filtering by project results in an empty list.
        it.ifEmpty { filteredNames }
    }
}

/**
 * Return recursively all ancestor directories of the given absolute [file], ordered along the path from
 * the parent of [file] to the root.
 */
fun getAllAncestorDirectories(file: String): List<String> {
    val result = mutableListOf<String>()

    var ancestorDir = File(file).parentFile
    while (ancestorDir != null) {
        result += ancestorDir.invariantSeparatorsPath
        ancestorDir = ancestorDir.parentFile
    }

    return result
}

/**
 * Return the longest parent directory that is common to all [files], or null if they have no directory in common.
 */
fun getCommonFileParent(files: Collection<File>): File? =
    files.map {
        it.normalize().absolutePath
    }.reduceOrNull { prefix, path ->
        prefix.commonPrefixWith(path)
    }?.let {
        val commonPrefix = File(it)
        if (commonPrefix.isDirectory) commonPrefix else commonPrefix.parentFile
    }

/**
 * Return the full path to the given executable file if it is in the system's PATH environment, or null otherwise.
 */
fun getPathFromEnvironment(executable: String): File? {
    fun String.expandVariable(referencePattern: Regex, groupName: String): String =
        replace(referencePattern) {
            val variableName = it.groups[groupName]!!.value
            Os.env[variableName] ?: variableName
        }

    val paths = Os.env["PATH"]?.splitToSequence(File.pathSeparatorChar).orEmpty()

    return if (Os.isWindows) {
        val referencePattern = Regex("%(?<reference>\\w+)%")

        paths.mapNotNull { path ->
            val expandedPath = path.expandVariable(referencePattern, "reference")
            resolveWindowsExecutable(File(expandedPath, executable))
        }.firstOrNull()
    } else {
        val referencePattern = Regex("\\$\\{?(?<reference>\\w+)}?")

        paths.map { path ->
            val expandedPath = path.expandVariable(referencePattern, "reference")
            File(expandedPath, executable)
        }.find { it.isFile }
    }
}

/**
 * Install both the [OrtAuthenticator] and the [OrtProxySelector] to handle proxy authentication. Return the
 * [OrtProxySelector] instance for further configuration.
 */
fun installAuthenticatorAndProxySelector(): OrtProxySelector {
    OrtAuthenticator.install()
    return OrtProxySelector.install()
}

/**
 * Return whether the given [url] points to Maven Central or not.
 */
fun isMavenCentralUrl(url: String) = url.matches(mavenCentralUrlPattern)

/**
 * Return the concatenated [strings] separated by [separator] whereas blank strings are omitted.
 */
fun joinNonBlank(vararg strings: String, separator: String = " - ") =
    strings.filter { it.isNotBlank() }.joinToString(separator)

/**
 * Normalize a string representing a [VCS URL][vcsUrl] to a common string form.
 */
fun normalizeVcsUrl(vcsUrl: String): String {
    var url = vcsUrl.trim().trimEnd('/')

    if (url.startsWith(":pserver:") || url.startsWith(":ext:")) {
        // Do not touch CVS URLs for now.
        return url
    }

    // URLs to Git repos may omit the scheme and use an scp-like URL that uses ":" to separate the host from the path,
    // see https://git-scm.com/docs/git-clone#_git_urls_a_id_urls_a. Make this an explicit ssh URL so it can be parsed
    // by Java's URI class.
    url = url.replace(Regex("^(.*)([a-zA-Z]+):([a-zA-Z]+)(.*)$")) {
        val tail = "${it.groupValues[1]}${it.groupValues[2]}/${it.groupValues[3]}${it.groupValues[4]}"
        if ("://" in url) tail else "ssh://$tail"
    }

    // Fixup scp-like Git URLs that do not use a ':' after the server part.
    if (url.startsWith("git@")) {
        url = "ssh://$url"
    }

    // Drop any non-SVN VCS name with "+" from the scheme.
    if (!url.startsWith("svn+")) {
        url = url.replace(Regex("^(.+)\\+(.+)(://.+)$")) {
            // Use the string to the right of "+" which should be the protocol.
            "${it.groupValues[2]}${it.groupValues[3]}"
        }
    }

    // If we have no protocol by now and the host is Git-specific, assume https.
    if (url.startsWith("github.com") || url.startsWith("gitlab.com")) {
        url = "https://$url"
    }

    // A hierarchical URI looks like
    //     [scheme:][//authority][path][?query][#fragment]
    // where a server-based "authority" has the syntax
    //     [user-info@]host[:port]
    val uri = url.toUri().getOrNull()

    if (uri == null || (uri.scheme == null && uri.path.isNotEmpty())) {
        // Fall back to a file if the URL is a Windows or Linux path.
        return File(url).toSafeUri().toString()
    }

    // Handle host-specific normalizations.
    if (uri.host != null) {
        when {
            uri.host.endsWith("github.com") || uri.host.endsWith("gitlab.com") -> {
                // Ensure the path to a repository ends with ".git".
                val path = uri.path.takeIf { path ->
                    path.endsWith(".git") || path.count { it == '/' } != 2
                } ?: "${uri.path}.git"

                return if (uri.scheme == "ssh") {
                    // Ensure the generic "git" user name is specified.
                    val host = uri.authority.let { if (it.startsWith("git@")) it else "git@$it" }
                    "ssh://$host$path"
                } else {
                    // Remove any user name and "www" prefix.
                    val host = uri.authority.substringAfter('@').removePrefix("www.")
                    "https://$host$path"
                }
            }
        }
    }

    return url
}

/**
 * Resolve the Windows [executable] to its full name including the optional extension.
 */
fun resolveWindowsExecutable(executable: File): File? {
    val extensions = Os.env["PATHEXT"]?.splitToSequence(File.pathSeparatorChar).orEmpty()
    return extensions.map { File(executable.path + it.lowercase()) }.find { it.isFile }
        ?: executable.takeIf { it.isFile }
}

/**
 * Temporarily set the specified system [properties] while executing [block]. Afterwards, previously set properties have
 * their original values restored and previously unset properties are cleared.
 */
fun <R> temporaryProperties(vararg properties: Pair<String, String?>, block: () -> R): R {
    val originalProperties = mutableListOf<Pair<String, String?>>()

    properties.forEach { (key, value) ->
        originalProperties += key to System.getProperty(key)
        value?.also { System.setProperty(key, it) } ?: System.clearProperty(key)
    }

    return try {
        block()
    } finally {
        originalProperties.forEach { (key, value) ->
            value?.also { System.setProperty(key, it) } ?: System.clearProperty(key)
        }
    }
}
