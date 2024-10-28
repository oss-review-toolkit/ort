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

package org.ossreviewtoolkit.utils.common

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.util.ClassUtil

import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import java.util.EnumSet
import java.util.Locale

import kotlin.io.path.deleteRecursively

/**
 * Call [also] only if the receiver is null, e.g. for error handling, and return the receiver in any case.
 */
inline fun <T> T.alsoIfNull(block: (T) -> Unit): T = this ?: also(block)

/**
 * Return a map that associates duplicates as identified by [keySelector] with belonging lists of collection entries.
 */
fun <T, K> Collection<T>.getDuplicates(keySelector: (T) -> K): Map<K, List<T>> =
    if (this is Set) emptyMap() else groupBy(keySelector).filter { it.value.size > 1 }

/**
 * Return a set of duplicate entries of a collection.
 */
fun <T> Collection<T>.getDuplicates(): Set<T> = getDuplicates { it }.keys

/**
 * Collapse consecutive values to a list of pairs that each denote a range. A single value is represented as a
 * range whose first and last elements are equal.
 */
fun Collection<Int>.collapseToRanges(): List<Pair<Int, Int>> {
    if (isEmpty()) return emptyList()

    val ranges = mutableListOf<Pair<Int, Int>>()

    val sortedValues = toSortedSet()
    val rangeBreaks = sortedValues.zipWithNext { a, b -> (a to b).takeIf { b != a + 1 } }.filterNotNull()

    var current = sortedValues.first()

    rangeBreaks.mapTo(ranges) { (last, first) ->
        (current to last).also { current = first }
    }

    ranges += current to sortedValues.last()

    return ranges
}

/**
 * Return a string of common-separated ranges as denoted by the list of pairs.
 */
fun Collection<Pair<Int, Int>>.prettyPrintRanges(): String =
    joinToString { (startValue, endValue) ->
        if (startValue == endValue) startValue.toString() else "$startValue-$endValue"
    }

/**
 * Format this [Double] as a string with the provided number of [decimalPlaces].
 */
fun Double.format(decimalPlaces: Int = 2) = "%.${decimalPlaces}f".format(this)

/**
 * Return an [EnumSet] that contains the elements of [this] and [other].
 */
operator fun <E : Enum<E>> EnumSet<E>.plus(other: EnumSet<E>): EnumSet<E> = EnumSet.copyOf(this).apply { addAll(other) }

/**
 * If the SHELL environment variable is set, return the absolute file with a leading "~" expanded to the current user's
 * home directory, otherwise return just the absolute file.
 */
fun File.expandTilde(): File = File(path.expandTilde()).absoluteFile

/**
 * Return true if and only if this file is a symbolic link.
 */
fun File.isSymbolicLink(): Boolean =
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
fun File.realFile(): File = toPath().toRealPath().toFile()

/**
 * Delete a directory recursively without following symbolic links (Unix) or junctions (Windows). If a [baseDirectory]
 * is provided, all empty parent directories along the path to [baseDirectory] are also deleted; [baseDirectory] itself
 * is not deleted. Throws an [IOException] if a directory or file could not be deleted.
 */
fun File.safeDeleteRecursively(baseDirectory: File? = null) {
    if (Os.isWindows) {
        // Note that Kotlin's `Path.deleteRecursively()` extension function cannot delete files on Windows that have the
        // read-only attribute set, so fall back to manually making them writable.
        walkBottomUp().onEnter { !it.isSymbolicLink() }.forEach { it.setWritable(true) }
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
 * Get the size of this [File] in mebibytes (MiB) with two decimal places as [String].
 */
val File.formatSizeInMib: String get() = "${length().bytesToMib().format()} MiB"

/**
 * Construct a "file:" URI in a safe way by never using a null authority for wider compatibility.
 */
fun File.toSafeUri(): URI {
    val fileUri = toURI()
    return URI("file", "", fileUri.path, fileUri.query, fileUri.fragment)
}

/**
 * Return the bytes equal to this [Int] number of kibibytes (KiB).
 */
inline val Int.kibibytes get(): Long = this * 1024L

/**
 * Return the bytes equal to this [Int] number of mebibytes (MiB).
 */
inline val Int.mebibytes get(): Long = kibibytes * 1024L

/**
 * Return the bytes equal to this [Int] number of gibibytes (GiB).
 */
inline val Int.gibibytes get(): Long = mebibytes * 1024L

/**
 * Return the next value in the iteration, or null if there is no next value.
 */
fun <T> Iterator<T>.nextOrNull() = if (hasNext()) next() else null

/*
 * Convenience function for [JsonNode] that returns an empty iterator if [JsonNode.fieldNames] is called on a null
 * object, or the field names otherwise.
 */
fun JsonNode?.fieldNamesOrEmpty(): Iterator<String> = this?.fieldNames() ?: ClassUtil.emptyIterator()

/*
 * Convenience function for [JsonNode] that returns an empty iterator if [JsonNode.fields] is called on a null object,
 * or the fields otherwise.
 */
fun JsonNode?.fieldsOrEmpty(): Iterator<Map.Entry<String, JsonNode>> = this?.fields() ?: ClassUtil.emptyIterator()

/**
 * Return true if and only if this [JsonNode]
 */
fun JsonNode.isNotEmpty(): Boolean = !isEmpty

/**
 * Convenience function for [JsonNode] that returns an empty string if [JsonNode.textValue] is called on a null object,
 * or the text value is null.
 */
fun JsonNode?.textValueOrEmpty(): String = this?.textValue().orEmpty()

/**
 * Merge two maps by iterating over the combined key set of both maps and applying [operation] to the entries for the
 * same key. Arguments passed to [operation] can be null if there is no entry for a key in the respective map.
 */
inline fun <K, V, W> Map<K, V>.zip(other: Map<K, V>, operation: (V?, V?) -> W): Map<K, W> =
    (keys + other.keys).associateWith { key ->
        operation(this[key], other[key])
    }

/**
 * Merge two maps by iterating over the combined key set of both maps and applying [operation] to the entries for the
 * same key. If there is no entry for a key in one of the maps, [default] is used as the value for that map.
 */
inline fun <K, V, W> Map<K, V>.zipWithDefault(other: Map<K, V>, default: V, operation: (V, V) -> W): Map<K, W> =
    (keys + other.keys).associateWith { key ->
        operation(this[key] ?: default, other[key] ?: default)
    }

/**
 * Merge two maps which have collections as values by creating the combined key set of both maps and merging the
 * collections. If there is no entry for a key in one of the maps, the value from the other map is used.
 */
fun <K, V : Collection<T>, T> Map<K, V>.zipWithCollections(other: Map<K, V>): Map<K, V> =
    zip(other) { a, b ->
        when {
            // When iterating over the combined key set, not both values can be null.
            a == null -> checkNotNull(b)
            b == null -> a
            else -> {
                @Suppress("UNCHECKED_CAST")
                (a + b) as V
            }
        }
    }

/**
 * Merge two maps which have sets as values by creating the combined key set of both maps and merging the sets. If there
 * is no entry for a key in one of the maps, the value from the other map is used.
 */
@JvmName("zipWithSets")
fun <K, V : Set<T>, T> Map<K, V>.zipWithCollections(other: Map<K, V>): Map<K, V> =
    zip(other) { a, b ->
        when {
            // When iterating over the combined key set, not both values can be null.
            a == null -> checkNotNull(b)
            b == null -> a
            else -> {
                @Suppress("UNCHECKED_CAST")
                (a + b) as V
            }
        }
    }

/**
 * Converts this [Number] from bytes to mebibytes (MiB).
 */
fun Number.bytesToMib(): Double = toDouble() / 1.mebibytes

/**
 * Trim leading and trailing whitespace, and collapse consecutive inner whitespace to a single space.
 */
fun String.collapseWhitespace() = trim().replace(CONSECUTIVE_WHITESPACE_REGEX, " ")

private val CONSECUTIVE_WHITESPACE_REGEX = Regex("\\s+")

/**
 * Return the string encoded for safe use as a file name or [emptyValue] encoded for safe use as a file name, if this
 * string is empty. Throws an exception if [emptyValue] is empty.
 */
fun String.encodeOr(emptyValue: String): String {
    require(emptyValue.isNotEmpty())

    return ifEmpty { emptyValue }.fileSystemEncode()
}

/**
 * Return the string encoded for safe use as a file name or "unknown", if the string is empty.
 */
fun String.encodeOrUnknown(): String = encodeOr("unknown")

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

/**
 * Return the string encoded for safe use as a file name. Also limit the length to 255 characters which is the maximum
 * length in most modern filesystems, see
 * [comparison of file system limits](https://en.wikipedia.org/wiki/Comparison_of_file_systems#Limits).
 */
fun String.fileSystemEncode() =
    percentEncode()
        // Percent-encoding does not necessarily encode some characters that are invalid in some file systems, so map
        // these afterwards.
        .replace(Regex("(^\\.|\\.$)"), "%2E")
        .take(255)

/**
 * True if the string is a valid [URI], false otherwise.
 */
fun String.isValidUri() = runCatching { URI(this) }.isSuccess

/**
 * A regular expression matching the non-linux line breaks "\r\n" and "\r".
 */
val NON_LINUX_LINE_BREAKS = Regex("\\r\\n?")

/**
 * Replace "\r\n" and "\r" line breaks with "\n".
 */
fun String.normalizeLineBreaks() = replace(NON_LINUX_LINE_BREAKS, "\n")

/**
 * Return the [percent-encoded](https://en.wikipedia.org/wiki/Percent-encoding) string.
 */
fun String.percentEncode(): String =
    java.net.URLEncoder.encode(this, Charsets.UTF_8)
        // As "encode" above actually performs encoding for forms, not for query strings, spaces are encoded as
        // "+" instead of "%20", so apply the proper mapping here afterwards ("+" in the original string is
        // encoded as "%2B").
        .replace("+", "%20")
        // "*" is a reserved character in RFC 3986.
        .replace("*", "%2A")
        // "~" is an unreserved character in RFC 3986.
        .replace("%7E", "~")

/**
 * Replace any username / password in the URI represented by this [String] with [userInfo]. If [userInfo] is null, the
 * username / password are stripped. Return the unmodified [String] if it does not represent a URI.
 */
fun String.replaceCredentialsInUri(userInfo: String? = null) =
    toUri {
        URI(it.scheme, userInfo, it.host, it.port, it.path, it.query, it.fragment).toString()
    }.getOrDefault(this)

/**
 * Return all substrings that do not contain any whitespace as a list.
 */
fun String.splitOnWhitespace(): List<String> = nonSpaceRegex.findAll(this).mapTo(mutableListOf()) { it.value }

private val nonSpaceRegex = Regex("\\S+")

/**
 * Return this string lower-cased except for the first character which is upper-cased.
 */
fun String.titlecase() = lowercase().uppercaseFirstChar()

/**
 * Return a [Result] that indicates whether the conversion of this [String] to a [URI] was successful.
 */
fun String.toUri() = runCatching { URI(this) }

/**
 * Return a [Result] that indicates whether the conversion of this [String] to a [URI] was successful, and [transform]
 * the [URI] if so.
 */
fun <R> String.toUri(transform: (URI) -> R) = toUri().mapCatching(transform)

/**
 * Return this string with (nested) single- and double-quotes removed. If [trimWhitespace] is true, then intermediate
 * whitespace is also removed, otherwise it is kept.
 */
fun String.unquote(trimWhitespace: Boolean = true) =
    trim { (trimWhitespace && it.isWhitespace()) || it == '\'' || it == '"' }

/**
 * Return this string with the first character upper-cased.
 */
fun String.uppercaseFirstChar() =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }

/**
 * If this string starts with [prefix], return the string without the prefix, otherwise return [missingPrefixValue].
 */
fun String?.withoutPrefix(prefix: String, missingPrefixValue: () -> String? = { null }): String? =
    this?.removePrefix(prefix)?.takeIf { it != this } ?: missingPrefixValue()

/**
 * If this string ends with [suffix], return the string without the suffix, otherwise return [missingSuffixValue].
 */
fun String?.withoutSuffix(suffix: String, missingSuffixValue: () -> String? = { null }): String? =
    this?.removeSuffix(suffix)?.takeIf { it != this } ?: missingSuffixValue()

/**
 * Recursively collect the messages of this [Throwable] and all its causes and join them to a single [String].
 */
fun Throwable.collectMessages(): String {
    fun Throwable.formatCauseAndSuppressedMessages(): String? =
        buildString {
            cause?.also {
                appendLine("Caused by: ${it.javaClass.simpleName}: ${it.message}")
                it.formatCauseAndSuppressedMessages()?.prependIndent()?.also(::append)
            }

            suppressed.forEach {
                appendLine("Suppressed: ${it.javaClass.simpleName}: ${it.message}")
                it.formatCauseAndSuppressedMessages()?.prependIndent()?.also(::append)
            }
        }.trim().takeUnless { it.isEmpty() }

    return listOfNotNull(
        "${javaClass.simpleName}: $message",
        formatCauseAndSuppressedMessages()
    ).joinToString("\n")
}

/**
 * Retrieve query parameters of this [URI]. Multiple values of a single key are supported if they are split by a comma,
 * or if keys are repeated as defined in RFC6570 section 3.2.9, see https://datatracker.ietf.org/doc/rfc6570.
 */
fun URI.getQueryParameters(): Map<String, List<String>> {
    if (query == null) return emptyMap()

    return query.split('&')
        .groupBy({ it.substringBefore('=') }, { it.substringAfter('=').split(',') })
        .mapValues { (_, v) -> v.flatten() }
}
