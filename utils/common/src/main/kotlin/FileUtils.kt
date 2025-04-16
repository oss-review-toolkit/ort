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

package org.ossreviewtoolkit.utils.common

import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes

import kotlin.io.path.deleteRecursively

/**
 * A comparator that sorts parent paths before child paths.
 */
val PATH_STRING_COMPARATOR = compareBy<String>({ path -> path.count { it == '/' } }, { it })

/**
 * A set of directories used by version control systems to store metadata. The list covers also version control systems
 * not supported by ORT, because such directories should never be considered.
 */
val VCS_DIRECTORIES = setOf(
    ".bzr",
    ".git",
    ".hg",
    ".repo",
    ".svn",
    "CVS",
    "CVSROOT"
)

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
 * Return the longest parent file that all [files] have in common, or a [File] with an empty path if they have no common
 * parent file.
 */
fun getCommonParentFile(files: Collection<File>): File =
    files.map {
        it.normalize().parent
    }.reduceOrNull { prefix, path ->
        prefix?.commonPrefixWith(path.orEmpty(), ignoreCase = Os.isWindows)
    }.orEmpty().let {
        File(it)
    }

/**
 * Get the size of this [File] in mebibytes (MiB) with two decimal places as [String].
 */
val File.formatSizeInMib: String get() = "${length().bytesToMib().format()} MiB"

/**
 * Return true if and only if this file is a symbolic link.
 */
val File.isSymbolicLink: Boolean get() =
    runCatching {
        // Note that we cannot use exists() to check beforehand whether a symbolic link exists to avoid a
        // NoSuchFileException to be thrown as it returns "false" e.g. for dangling Windows junctions.
        Files.readAttributes(toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS).let {
            it.isSymbolicLink || (Os.isWindows && it.isOther)
        }
    }.getOrDefault(false)

/**
 * Resolve the file to the real underlying file. In contrast to Java's [File.getCanonicalFile], this also works to
 * resolve symbolic links on Windows.
 */
val File.realFile: File get() = toPath().toRealPath().toFile()

/**
 * If the SHELL environment variable is set, return the absolute file with a leading "~" expanded to the current user's
 * home directory, otherwise return just the absolute file.
 */
fun File.expandTilde(): File = File(path.expandTilde()).absoluteFile

/**
 * Delete a directory recursively without following symbolic links (Unix) or junctions (Windows). If a [baseDirectory]
 * is provided, all empty parent directories along the path to [baseDirectory] are also deleted; [baseDirectory] itself
 * is not deleted. Throws an [IOException] if a directory or file could not be deleted.
 */
fun File.safeDeleteRecursively(baseDirectory: File? = null) {
    if (Os.isWindows) {
        // Note that Kotlin's `Path.deleteRecursively()` extension function cannot delete files on Windows that have the
        // read-only attribute set, so fall back to manually making them writable.
        walkBottomUp().onEnter { !it.isSymbolicLink }.forEach { it.setWritable(true) }
    }

    // Note that Kotlin's `File.deleteRecursively()` extension function cannot delete files on Linux with unmappable
    // characters in their names, so use `Path.deleteRecursively()` instead.
    toPath().deleteRecursively()

    if (baseDirectory == this) {
        safeMkdirs()
        return
    }

    if (baseDirectory != null) {
        var parent = parentFile
        while (parent != null && parent != baseDirectory && parent.delete()) {
            parent = parent.parentFile
        }
    }
}

/**
 * Create all missing intermediate directories without failing if any already exists. Returns the [File] it was called
 * on if successful, otherwise throws an [IOException].
 */
fun File.safeMkdirs(): File {
    // Do not blindly trust mkdirs() returning "false" as it can fail for edge-cases like
    // File(File("/tmp/parent1/parent2"), "/").mkdirs() if parent1 does not exist, although the directory is
    // successfully created.
    if (isDirectory || mkdirs() || isDirectory) {
        return this
    }

    throw IOException("Could not create directory '$absolutePath'.")
}

/**
 * Search [this] directory upwards towards the root until a contained subdirectory called [searchDirName] is found and
 * return the parent of [searchDirName], or return null if no such directory is found.
 */
fun File.searchUpwardsForSubdirectory(searchDirName: String): File? {
    if (!isDirectory) return null

    var currentDir: File? = absoluteFile

    while (currentDir != null && !currentDir.resolve(searchDirName).isDirectory) {
        currentDir = currentDir.parentFile
    }

    return currentDir
}

/**
 * Construct a "file:" URI in a safe way by never using a null authority for wider compatibility.
 */
fun File.toSafeUri(): URI {
    val fileUri = toURI()
    return URI("file", "", fileUri.path, fileUri.query, fileUri.fragment)
}

/**
 * If the SHELL environment variable is set, return the string with a leading "~" expanded to the current user's home
 * directory, otherwise return the string unchanged.
 */
fun String.expandTilde(): String =
    if (Os.env["SHELL"] != null) {
        replace(Regex("^~"), Regex.escapeReplacement(System.getProperty("user.home")))
    } else {
        this
    }
