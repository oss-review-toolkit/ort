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
import java.io.InputStream
import java.security.MessageDigest
import java.util.EnumSet

/**
 * A list of directories used by version control systems to store metadata.
 */
val VCS_DIRECTORIES = listOf(
    ".git",
    ".hg",
    ".repo",
    ".svn",
    "CVS",
    "CVSROOT"
)

/**
 * Calculate the [digest] on the data from the given [file].
 */
fun calculateHash(file: File, digest: MessageDigest = MessageDigest.getInstance("SHA-1")): ByteArray =
    file.inputStream().use { calculateHash(it, digest) }

/**
 * Calculate the [digest] on the data from the given [string].
 */
fun calculateHash(string: String, digest: MessageDigest = MessageDigest.getInstance("SHA-1")): ByteArray =
    string.byteInputStream().use { calculateHash(it, digest) }

/**
 * Calculate the [digest] on the data from the given [inputStream]. The caller is responsible for closing the stream.
 */
fun calculateHash(inputStream: InputStream, digest: MessageDigest = MessageDigest.getInstance("SHA-1")): ByteArray {
    // 4MB has been chosen rather arbitrarily, hoping that it provides good performance while not consuming a
    // lot of memory at the same time, also considering that this function could potentially be run on multiple
    // threads in parallel.
    val buffer = ByteArray(4 * 1024 * 1024)

    var length: Int
    while (inputStream.read(buffer).also { length = it } > 0) {
        digest.update(buffer, 0, length)
    }

    return digest.digest()
}

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

private val mavenCentralUrlPattern = Regex("^https?://repo1?\\.maven(\\.apache)?\\.org(/.*)?$")

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
