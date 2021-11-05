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

package org.ossreviewtoolkit.utils.common

import java.io.File
import java.util.EnumSet

/**
 * A list of directories used by version control systems to store metadata.
 */
val VCS_DIRECTORIES = listOf(
    ".git",
    ".hg",
    ".repo",
    ".svn",
    "CVS"
)

/**
 * A Kotlin-style convenience function to replace EnumSet.of() and EnumSet.noneOf().
 */
inline fun <reified T : Enum<T>> enumSetOf(vararg elems: T): EnumSet<T> =
    EnumSet.noneOf(T::class.java).apply { addAll(elems) }

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
 * Return the concatenated [strings] separated by [separator] whereas blank strings are omitted.
 */
fun joinNonBlank(vararg strings: String, separator: String = " - ") =
    strings.filter { it.isNotBlank() }.joinToString(separator)

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
