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

@file:Suppress("TooManyFunctions")

package org.ossreviewtoolkit.utils

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.util.ClassUtil

import com.vdurmont.semver4j.Semver

import java.io.File
import java.io.IOException
import java.lang.NumberFormatException
import java.net.URI
import java.net.URISyntaxException
import java.net.URL
import java.nio.file.CopyOption
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest

/**
 * Return a string of hexadecimal digits representing the bytes in the array.
 */
fun ByteArray.toHexString(): String = joinToString("") { String.format("%02x", it) }

/**
 * Format this [Double] as a string with the provided number of [decimalPlaces].
 */
fun Double.format(decimalPlaces: Int = 2) = "%.${decimalPlaces}f".format(this)

/**
 * If the SHELL environment variable is set, return the absolute file with a leading "~" expanded to the current user's
 * home directory, otherwise return just the absolute file.
 */
fun File.expandTilde(): File = File(path.expandTilde()).absoluteFile

/**
 * Return the hexadecimal digest of the given hash [algorithm] for this [File].
 */
fun File.hash(algorithm: String = "SHA-1"): String =
    inputStream().use { inputStream ->
        // 4MB has been chosen rather arbitrary hoping that it provides a good enough performance while not consuming
        // a lot of memory at the same time, also considering that this function could potentially be run on multiple
        // threads in parallel.
        val buffer = ByteArray(4 * 1024 * 1024)
        val digest = MessageDigest.getInstance(algorithm)

        var length: Int
        while (inputStream.read(buffer).also { length = it } > 0) {
            digest.update(buffer, 0, length)
        }

        digest.digest().toHexString()
    }

/**
 * Return true if and only if this file is a symbolic link.
 */
fun File.isSymbolicLink(): Boolean =
    try {
        // Note that we cannot use exists() to check beforehand whether a symbolic link exists to avoid a
        // NoSuchFileException to be thrown as it returns "false" e.g. for dangling Windows junctions.
        Files.readAttributes(toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS).let {
            it.isSymbolicLink || (Os.isWindows && it.isOther)
        }
    } catch (e: NoSuchFileException) {
        false
    }

/**
 * Resolve the file to the real underlying file. In contrast to Java's [File.getCanonicalFile], this also works to
 * resolve symbolic links on Windows.
 */
fun File.realFile(): File = toPath().toRealPath().toFile()

/**
 * Copy files recursively without following symbolic links (Unix) or junctions (Windows).
 */
fun File.safeCopyRecursively(target: File, overwrite: Boolean = false) {
    if (!exists()) {
        return
    }

    val sourcePath = absoluteFile.toPath()
    val targetPath = target.absoluteFile.toPath()

    val copyOptions = mutableListOf<CopyOption>(LinkOption.NOFOLLOW_LINKS).apply {
        if (overwrite) add(StandardCopyOption.REPLACE_EXISTING)
    }.toTypedArray()

    Files.walkFileTree(sourcePath, object : SimpleFileVisitor<Path>() {
        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
            // Note that although FileVisitOption.FOLLOW_LINKS is not set, this would still follow junctions on Windows,
            // so do a better check here.
            if (dir.toFile().isSymbolicLink()) return FileVisitResult.SKIP_SUBTREE

            val targetDir = targetPath.resolve(sourcePath.relativize(dir))
            targetDir.toFile().safeMkdirs()

            return FileVisitResult.CONTINUE
        }

        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            val targetFile = targetPath.resolve(sourcePath.relativize(file))
            Files.copy(file, targetFile, *copyOptions)

            return FileVisitResult.CONTINUE
        }
    })
}

/**
 * Delete files recursively without following symbolic links (Unix) or junctions (Windows). If [force] is `true`, files
 * which were not deleted in the first attempt are set to be writable and then tried to be deleted again. If
 * [baseDirectory] is given, all empty parent directories along the path to [baseDirectory] are also deleted;
 * [baseDirectory] itself is not deleted. Throws an [IOException] if a file could not be deleted.
 */
fun File.safeDeleteRecursively(force: Boolean = false, baseDirectory: File? = null) {
    if (isDirectory && !isSymbolicLink()) {
        Files.newDirectoryStream(toPath()).use { stream ->
            stream.forEach { path ->
                path.toFile().safeDeleteRecursively(force)
            }
        }
    }

    if (!delete() && force && setWritable(true)) {
        // Try again.
        delete()
    }

    if (baseDirectory != null) {
        var parent = parentFile
        while (parent != null && parent != baseDirectory && parent.delete()) {
            parent = parent.parentFile
        }
    }

    if (exists()) throw IOException("Could not delete file '$absolutePath'.")
}

/**
 * Create all missing intermediate directories without failing if any already exists.
 *
 * @throws IOException if any missing directory could not be created.
 */
fun File.safeMkdirs() {
    // Do not blindly trust mkdirs() returning "false" as it can fail for edge-cases like
    // File(File("/tmp/parent1/parent2"), "/").mkdirs() if parent1 does not exist, although the directory is
    // successfully created.
    if (isDirectory || mkdirs() || isDirectory) {
        return
    }

    throw IOException("Could not create directory '$absolutePath'.")
}

/**
 * Search [this] directory upwards towards the root until a file called [searchFileName] is found and return this file,
 * or return null if no such file is found.
 */
fun File.searchUpwardsForFile(searchFileName: String, ignoreCase: Boolean = false): File? {
    fun resolveFile(dir: File, fileName: String, ignoreCase: Boolean): File? {
        val files = dir.list() ?: return null

        return files.filter { it.equals(fileName, ignoreCase = ignoreCase) }
            .map { dir.resolve(it) }
            .find { it.isFile }
    }

    if (!isDirectory) return null

    var currentDir: File? = absoluteFile
    var currentFile = currentDir?.let { resolveFile(it, searchFileName, ignoreCase) }
    while (currentDir != null && currentFile == null) {
        currentDir = currentDir.parentFile ?: break
        currentFile = resolveFile(currentDir, searchFileName, ignoreCase)
    }

    return currentFile
}

/**
 * Search [this] directory upwards towards the root until a contained sub-directory called [searchDirName] is found and
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
fun File.toSafeURI(): URI {
    val fileUri = toURI()
    return URI("file", "", fileUri.path, fileUri.query, fileUri.fragment)
}

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
 * Convenience function for [JsonNode] that returns an empty string if [JsonNode.textValue] is called on a null object,
 * or the text value is null.
 */
fun JsonNode?.textValueOrEmpty(): String = this?.textValue().orEmpty()

/**
 * Merge two maps by iterating over the combined key set of both maps and applying [operation] to the entries for the
 * same key. Parameters passed to [operation] can be null if there is no entry for a key in one of the maps.
 */
inline fun <K, V, W> Map<K, V>.zip(other: Map<K, V>, operation: (V?, V?) -> W): Map<K, W> =
    (this.keys + other.keys).associateWith { key ->
        operation(this[key], other[key])
    }

/**
 * Merge two maps by iterating over the combined key set of both maps and applying [operation] to the entries for the
 * same key. If there is no entry for a key in one of the maps, [default] is used.
 */
inline fun <K, V, W> Map<K, V>.zipWithDefault(other: Map<K, V>, default: V, operation: (V, V) -> W): Map<K, W> =
    (this.keys + other.keys).associateWith { key ->
        operation(this[key] ?: default, other[key] ?: default)
    }

/**
 * Converts this [Number] from bytes to mebibytes (MiB).
 */
fun Number.bytesToMib(): Double = toDouble() / (1024 * 1024)

/**
 * Return the string encoded for safe use as a file name or "unknown", if the string is empty.
 */
fun String.encodeOrUnknown(): String = encodeOr("unknown")

/**
 * Return the string encoded for safe use as a file name or [emptyValue] encoded for safe use as a file name, if this
 * string is empty. Throws an exception if [emptyValue] is empty.
 */
fun String.encodeOr(emptyValue: String): String {
    require(emptyValue.isNotEmpty())

    return ifEmpty { emptyValue }.fileSystemEncode()
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
 * Return true if the string represents a true value, otherwise return false.
 */
fun String?.isTrue() = this?.toBoolean() ?: false

/**
 * Return the [percent-encoded](https://en.wikipedia.org/wiki/Percent-encoding) string.
 */
fun String.percentEncode(): String =
    java.net.URLEncoder.encode(this, "UTF-8")
        // As "encode" above actually performs encoding for forms, not for query strings, spaces are encoded as
        // "+" instead of "%20", so apply the proper mapping here afterwards ("+" in the original string is
        // encoded as "%2B").
        .replace("+", "%20")
        // "*" is a reserved character in RFC 3986.
        .replace("*", "%2A")
        // "~" is an unreserved character in RFC 3986.
        .replace("%7E", "~")

/**
 * True if the string is a valid semantic version of the given [type], false otherwise.
 */
fun String.isSemanticVersion(type: Semver.SemverType = Semver.SemverType.STRICT) =
    runCatching { Semver(this, type) }.isSuccess

/**
 * True if the string is a valid [URI], false otherwise.
 */
fun String.isValidUri() = runCatching { URI(this) }.isSuccess

/**
 * True if the string is a valid [URL], false otherwise.
 */
fun String.isValidUrl() = runCatching { URL(this) }.isSuccess

/**
 * A regular expression matching the non-linux line breaks "\r\n" and "\r".
 */
val NON_LINUX_LINE_BREAKS = Regex("\\r\\n?")

/**
 * Replace "\r\n" and "\r" line breaks with "\n".
 */
fun String.normalizeLineBreaks() = replace(NON_LINUX_LINE_BREAKS, "\n")

/**
 * Strip any user name / password off the URL represented by this [String]. Return the unmodified [String] if it does
 * not represent a URL or if it does not include a user name.
 */
fun String.stripCredentialsFromUrl() =
    try {
        // Use an URI instead of an URL as the former allows to specify the userInfo separately.
        URI(this).let {
            URI(it.scheme, null, it.host, it.port, it.path, it.query, it.fragment).toString()
        }
    } catch (e: URISyntaxException) {
        this
    }

/**
 * Recursively collect the messages of this [Throwable] and all its causes.
 */
fun Throwable.collectMessages(): List<String> {
    val messages = mutableListOf<String>()
    var cause: Throwable? = this
    while (cause != null) {
        val suppressed = cause.suppressed.joinToString("") { "\nSuppressed: ${it.javaClass.simpleName}: ${it.message}" }
        messages += "${cause.javaClass.simpleName}: ${cause.message}$suppressed"
        cause = cause.cause
    }
    return messages
}

/**
 * Recursively collect the messages of this [Throwable] and all its causes and join them to a single [String].
 */
fun Throwable.collectMessagesAsString() = collectMessages().joinToString("\nCaused by: ")

/**
 * Print the stack trace of the [Throwable] if [printStackTrace] is set to true.
 */
fun Throwable.showStackTrace() {
    // We cannot use a function expression for a single "if"-statement, see
    // https://discuss.kotlinlang.org/t/if-operator-in-function-expression/7227.
    if (printStackTrace) printStackTrace()
}

/**
 * Check whether the URI has a fragment that looks like a VCS revision.
 */
fun URI.hasRevisionFragment() = fragment?.let { Regex("[a-fA-F0-9]{7,}$").matches(it) } == true

/**
 * Return a version string with leading zeros of components stripped.
 */
fun stripLeadingZerosFromVersion(version: String) =
    version.split(".").joinToString(".") {
        try {
            it.toInt().toString()
        } catch (e: NumberFormatException) {
            it
        }
    }
