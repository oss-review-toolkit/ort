/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package com.here.ort.utils

import java.io.File
import java.net.URI
import java.net.URISyntaxException
import java.security.Permission
import java.util.Collections

@Suppress("UnsafeCast")
val log = org.slf4j.LoggerFactory.getLogger({}.javaClass) as ch.qos.logback.classic.Logger

/**
 * Global variable that gets toggled by a command line parameter parsed in the main entry points of the modules.
 */
var printStackTrace = false

/**
 * The name of the ORT configuration file.
 */
const val ORT_CONFIG_FILENAME = ".ort.yml"

/**
 * Ordinal for mandatory program parameters.
 */
const val PARAMETER_ORDER_MANDATORY = 0

/**
 * Ordinal for optional program parameters.
 */
const val PARAMETER_ORDER_OPTIONAL = 1

/**
 * Ordinal for logging related program parameters.
 */
const val PARAMETER_ORDER_LOGGING = 2

/**
 * Ordinal for the help program parameter.
 */
const val PARAMETER_ORDER_HELP = 100

/**
 * Check whether the specified two or more collections have no elemets in common.
 */
fun disjoint(c1: Collection<*>, c2: Collection<*>, vararg cN: Collection<*>): Boolean {
    val c = listOf(c1, c2, *cN)

    for (a in c.indices) {
        for (b in a + 1 until c.size) {
            if (!Collections.disjoint(c[a], c[b])) return false
        }
    }

    return true
}

/**
 * Filter a list of [names] to include only those that likely belong to the given [version] of an optional [project].
 */
fun filterVersionNames(version: String, names: List<String>, project: String? = null): List<String> {
    if (version.isBlank() || names.isEmpty()) return emptyList()

    // If there are full matches, return them right away.
    names.filter { it.equals(version, true) }.let { if (it.isNotEmpty()) return it }

    // The list of supported version separators.
    val versionSeparators = listOf('-', '_', '.')
    val versionHasSeparator = versionSeparators.any { version.contains(it) }

    // Create variants of the version string to recognize.
    data class VersionVariant(val name: String, val separators: List<Char>)

    val versionLower = version.toLowerCase()
    val versionVariants = mutableListOf(VersionVariant(versionLower, versionSeparators))

    val separatorRegex = Regex(versionSeparators.joinToString("", "[", "]"))
    versionSeparators.forEach {
        versionVariants += VersionVariant(versionLower.replace(separatorRegex, it.toString()), listOf(it))
    }

    val filteredNames = names.filter {
        val name = it.toLowerCase()

        versionVariants.any { versionVariant ->
            when {
                // Allow to ignore suffixes in names that are separated by something else than the current
                // separator, e.g. for version "3.3.1" accept "3.3.1-npm-packages" but not "3.3.1.0".
                name.startsWith(versionVariant.name) -> {
                    val tail = name.removePrefix(versionVariant.name)
                    tail.firstOrNull() !in versionVariant.separators
                }

                // Allow to ignore prefixes in names that are separated by something else than the current
                // separator, e.g. for version "0.10" accept "docutils-0.10" but not "1.0.10".
                name.endsWith(versionVariant.name) -> {
                    val head = name.removeSuffix(versionVariant.name)
                    val last = head.lastOrNull()
                    val forelast = head.dropLast(1).lastOrNull()

                    val currentSeparators = if (versionHasSeparator) versionVariant.separators else versionSeparators

                    // Full match with the current version variant.
                    last == null ||
                            // The prefix does not end with the current separators or a digit.
                            (last !in currentSeparators && !last.isDigit()) ||
                            // The prefix ends with the current separators but the forelast character is not a digit.
                            (last in currentSeparators && (forelast == null || !forelast.isDigit())) ||
                            // The prefix ends with 'v' and the forelast character is a separator.
                            (last == 'v' && (forelast == null || forelast in currentSeparators))
                }

                else -> false
            }
        }
    }

    return filteredNames.filter {
        // startsWith("") returns "true" for any string, so we get an unfiltered list if "project" is "null".
        it.startsWith(project ?: "")
    }.let {
        // Fall back to the original list if filtering by project results in an empty list.
        if (it.isEmpty()) filteredNames else it
    }
}

/**
 * Return the longest prefix that is common to all [files].
 */
fun getCommonFilePrefix(files: Collection<File>) =
        files.map {
            it.normalize().absolutePath
        }.reduce { prefix, path ->
            prefix.commonPrefixWith(path)
        }.let {
            File(it)
        }

/**
 * Return the full path to the given executable file if it is in the system's PATH environment, or null otherwise.
 */
fun getPathFromEnvironment(executable: String): File? {
    val paths = System.getenv("PATH")?.splitToSequence(File.pathSeparatorChar) ?: emptySequence()

    val executables = if (OS.isWindows) {
        // Get the list of executable file extensions without the leading dot each.
        val pathExt = System.getenv("PATHEXT")?.let {
            it.split(File.pathSeparatorChar).map { ext -> ext.toLowerCase().removePrefix(".") }
        } ?: emptyList()

        if (executable.substringAfterLast(".").toLowerCase() !in pathExt) {
            // Specifying an executable's file extension is optional on Windows, so try all of them in order, but still
            // also try the unmodified executable name as a fall-back.
            pathExt.map { "$executable.$it" } + executable
        } else {
            listOf(executable)
        }
    } else {
        listOf(executable)
    }

    paths.forEach { path ->
        executables.forEach {
            val pathToExecutable = File(path, it)
            if (pathToExecutable.isFile) {
                return pathToExecutable
            }
        }
    }

    return null
}

/**
 * Return the current user's home directory.
 */
fun getUserHomeDirectory() = File(System.getProperty("user.home"))

/**
 * Return the directory to store user-specific ORT data in.
 */
fun getUserOrtDirectory() = getUserHomeDirectory().resolve(".ort")

/**
 * Normalize a VCS URL by converting it to a common pattern.
 *
 * @param vcsUrl The URL to normalize.
 */
fun normalizeVcsUrl(vcsUrl: String): String {
    var url = vcsUrl.trimEnd('/')

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
    val uri = try {
        URI(url)
    } catch (e: URISyntaxException) {
        // Fall back to a file if the URL is a Windows path.
        return File(url).toSafeURI().toString()
    }

    if (uri.scheme == null && uri.path.isNotEmpty()) {
        // Fall back to a file if the URL is a Linux path.
        return File(url).toSafeURI().toString()
    }

    // Handle host-specific normalizations.
    if (uri.host != null) {
        when {
            uri.host.endsWith("github.com") || uri.host.endsWith("gitlab.com") -> {
                // Ensure the path ends in ".git".
                val path = uri.path.takeIf { Regex("\\.git(/|$)") in it } ?: "${uri.path}.git"

                return if (uri.scheme == "ssh") {
                    // Ensure the generic "git" user name is specified.
                    val host = uri.authority.let { if (it.startsWith("git@")) it else "git@$it" }
                    "ssh://$host$path"
                } else {
                    // Remove any user name and "www" prefix.
                    val host = uri.authority.substringAfter("@").removePrefix("www.")
                    "https://$host$path"
                }
            }
        }
    }

    return url
}

/**
 * Temporarily set the specified system [properties] while executing [block]. Afterwards, previously set properties have
 * their original values restored and previously unset properties are cleared.
 */
fun temporaryProperties(vararg properties: Pair<String, String>, block: () -> Unit) {
    val originalProperties = mutableListOf<Pair<String, String?>>()

    properties.forEach { (key, value) ->
        originalProperties += key to System.getProperty(key)
        System.setProperty(key, value)
    }

    try {
        block()
    } finally {
        originalProperties.forEach { (key, value) ->
            value?.let { System.setProperty(key, it) } ?: System.clearProperty(key)
        }
    }
}

/**
 * Trap a system exit call in [block]. This is useful e.g. when calling the Main class of a command line tool
 * programmatically. Returns the exit code or null if no system exit call was trapped.
 */
fun trapSystemExitCall(block: () -> Unit): Int? {
    // Define a custom security exception which we can catch in order to ignore it.
    class ExitTrappedException : SecurityException()

    var exitCode: Int? = null
    val originalSecurityManager = System.getSecurityManager()

    System.setSecurityManager(object : SecurityManager() {
        override fun checkPermission(perm: Permission) {
            if (perm.name.startsWith("exitVM")) {
                exitCode = perm.name.substringAfter('.').toIntOrNull()
                throw ExitTrappedException()
            }

            originalSecurityManager?.checkPermission(perm)
        }
    })

    try {
        block()
    } catch (e: ExitTrappedException) {
        // Ignore our own custom security exception.
    } finally {
        System.setSecurityManager(originalSecurityManager)
    }

    return exitCode
}
