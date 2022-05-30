/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2021 Bosch.IO GmbH
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

@file:Suppress("MatchingDeclarationName", "TooManyFunctions")

package org.ossreviewtoolkit.utils.common

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.zip.Deflater

import kotlin.io.path.createTempDirectory

import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.ar.ArArchiveEntry
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel

enum class ArchiveType(vararg val extensions: String) {
    SEVENZIP(".7z"),
    ZIP(".aar", ".egg", ".jar", ".war", ".whl", ".zip"),

    TAR(".gem", ".tar"),
    TAR_BZIP2(".tar.bz2", ".tbz2"),
    TAR_GZIP(".crate", ".tar.gz", ".tgz"),
    TAR_XZ(".tar.xz", ".txz"),

    DEB(".deb", ".udeb"),

    NONE("");

    companion object {
        fun getType(filename: String): ArchiveType {
            val lowerName = filename.lowercase()
            return (enumValues<ArchiveType>().asList() - NONE).find { type ->
                type.extensions.any { lowerName.endsWith(it) }
            } ?: NONE
        }
    }
}

/**
 * Unpack the [File] to [targetDirectory] using [filter] to select only the entries of interest. If an archive type
 * other than [ArchiveType.NONE] is specified in [forceArchiveType], use this one; otherwise, try to detect the type
 * based on the file extension. Throw an [IOException] if unpacking fails.
 */
fun File.unpack(
    targetDirectory: File,
    forceArchiveType: ArchiveType = ArchiveType.NONE,
    filter: (ArchiveEntry) -> Boolean = { true }
) =
    when (forceArchiveType.takeUnless { it == ArchiveType.NONE } ?: ArchiveType.getType(name)) {
        ArchiveType.SEVENZIP -> unpack7Zip(targetDirectory, filter)
        ArchiveType.ZIP -> unpackZip(targetDirectory, filter)

        ArchiveType.TAR -> inputStream().use { it.unpackTar(targetDirectory, filter) }
        ArchiveType.TAR_BZIP2 -> inputStream().use { BZip2CompressorInputStream(it).unpackTar(targetDirectory, filter) }
        ArchiveType.TAR_GZIP -> inputStream().use { GzipCompressorInputStream(it).unpackTar(targetDirectory, filter) }
        ArchiveType.TAR_XZ -> inputStream().use { XZCompressorInputStream(it).unpackTar(targetDirectory, filter) }

        ArchiveType.DEB -> unpackDeb(targetDirectory, filter)

        ArchiveType.NONE -> {
            throw IOException("Unable to guess compression scheme from file name '$name'.")
        }
    }

/**
 * Try to unpack this [File] of an unknown archive type to [targetDirectory] using [filter] to select only the entries
 * of interest. Use the file name to guess the archive type. If this is not possible due to a missing or unknown file
 * extension, try out the supported archive types one after the other.
 */
fun File.unpackTryAllTypes(targetDirectory: File, filter: (ArchiveEntry) -> Boolean = { true }) {
    val typeFromName = ArchiveType.getType(name)
    val suppressedExceptions = mutableListOf<Throwable>()

    enumValues<ArchiveType>().mapNotNullTo(mutableListOf(typeFromName)) { type ->
        type.takeUnless { type == typeFromName || type == ArchiveType.NONE }
    }.find { archiveType ->
        runCatching {
            unpack(targetDirectory, forceArchiveType = archiveType, filter)
        }.onFailure {
            suppressedExceptions += IOException("Unpacking '$this' as $archiveType failed.", it)
        }.isSuccess
    } ?: throw IOException("Unable to unpack '$this'. This file is not a supported archive type.").apply {
        suppressedExceptions.forEach(::addSuppressed)
    }
}

/**
 * Unpack the [File] assuming it is a 7-Zip archive. This implementation ignores empty directories and symbolic links
 * and all entries not matched by the given [filter].
 */
fun File.unpack7Zip(targetDirectory: File, filter: (ArchiveEntry) -> Boolean = { true }) {
    SevenZFile(this).use { zipFile ->
        while (true) {
            val entry = zipFile.nextEntry ?: break

            @Suppress("ComplexCondition")
            if (entry.isDirectory || entry.isAntiItem || File(entry.name).isAbsolute || !filter(entry)) {
                continue
            }

            val target = targetDirectory.resolve(entry.name)

            // There is no guarantee that directory entries appear before file entries, so ensure that the parent
            // directory for a file exists.
            target.parentFile.safeMkdirs()

            target.outputStream().use { output ->
                zipFile.getInputStream(entry).copyTo(output)
            }
        }
    }
}

/**
 * Unpack the [ByteArray] assuming it is a Zip archive, ignoring entries not matched by [filter].
 */
fun ByteArray.unpackZip(targetDirectory: File, filter: (ArchiveEntry) -> Boolean = { true }) =
    ZipFile(SeekableInMemoryByteChannel(this)).unpack(targetDirectory, filter)

/**
 * Unpack the [File] assuming it is a Zip archive ignoring all entries not matched by [filter].
 */
fun File.unpackZip(targetDirectory: File, filter: (ArchiveEntry) -> Boolean = { true }) =
    ZipFile(this).unpack(targetDirectory, filter)

/**
 * Unpack the [ZipFile]. In contrast to [InputStream.unpackZip] this properly parses the ZIP's central directory, see
 * https://commons.apache.org/proper/commons-compress/zip.html#ZipArchiveInputStream_vs_ZipFile.
 */
private fun ZipFile.unpack(targetDirectory: File, filter: (ArchiveEntry) -> Boolean) =
    use { zipFile ->
        val entries = zipFile.entries

        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()

            @Suppress("ComplexCondition")
            if (entry.isDirectory || entry.isUnixSymlink || File(entry.name).isAbsolute || !filter(entry)) {
                continue
            }

            val target = targetDirectory.resolve(entry.name)

            // There is no guarantee that directory entries appear before file entries, so ensure that the parent
            // directory for a file exists.
            target.parentFile.safeMkdirs()

            target.outputStream().use { output ->
                zipFile.getInputStream(entry).copyTo(output)
            }

            copyExecutableModeBit(target, entry.unixMode)
        }
    }

/**
 * A list with file names that are expected to be contained in a Debian package archive file.
 */
internal val DEB_NESTED_ARCHIVES = listOf("data.tar.xz", "control.tar.xz")

/**
 * Unpack the [File] assuming it is a Debian archive. The nested top-level "data" and "control" TAR archives are
 * unpacked to the provided [targetDirectory] into subdirectories of the respective names. The [filter] function is
 * applied to the contents of the TAR archives so that [ArchiveEntry]s that do not match are ignored.
 */
fun File.unpackDeb(targetDirectory: File, filter: (ArchiveEntry) -> Boolean = { true }) {
    val tempDir = createTempDirectory("unpackDeb").toFile()

    try {
        ArArchiveInputStream(inputStream()).unpack(
            tempDir,
            { entry -> entry.isDirectory || File(entry.name).isAbsolute },
            { entry -> (entry as ArArchiveEntry).mode }
        )

        DEB_NESTED_ARCHIVES.forEach { name ->
            val subDirectoryName = name.substringBefore('.')
            val subDirectory = targetDirectory.resolve(subDirectoryName).apply { safeMkdirs() }
            val file = tempDir.resolve(name)
            file.unpack(subDirectory, filter = filter)
        }
    } finally {
        tempDir.safeDeleteRecursively(force = true)
    }
}

/**
 * Unpack the [InputStream] to [targetDirectory] assuming that it is a ZIP archive. This implementation ignores empty
 * directories and symbolic links.
 */
fun InputStream.unpackZip(targetDirectory: File) =
    ZipArchiveInputStream(this).unpack(
        targetDirectory,
        { entry -> (entry as ZipArchiveEntry).let { it.isDirectory || it.isUnixSymlink || File(it.name).isAbsolute } },
        { entry -> (entry as ZipArchiveEntry).unixMode }
    )

/**
 * Unpack the [InputStream] to [targetDirectory] assuming that it is a tape archive (TAR). This implementation ignores
 * empty directories and symbolic links and all archive entries not accepted by [filter].
 */
fun InputStream.unpackTar(targetDirectory: File, filter: (ArchiveEntry) -> Boolean = { true }) =
    TarArchiveInputStream(this).unpack(
        targetDirectory,
        { entry -> !(entry as TarArchiveEntry).isFile || File(entry.name).isAbsolute || !filter(entry) },
        { entry -> (entry as TarArchiveEntry).mode }
    )

/**
 * Unpack this [ArchiveInputStream] to the [targetDirectory], skipping all entries for which [shouldSkip] returns true,
 * and using what [mode] returns as the file mode bits.
 */
private fun ArchiveInputStream.unpack(
    targetDirectory: File,
    shouldSkip: (ArchiveEntry) -> Boolean,
    mode: (ArchiveEntry) -> Int
) =
    use { input ->
        var processed = false

        while (true) {
            val entry = input.nextEntry ?: break
            processed = true

            if (shouldSkip(entry)) continue

            val target = targetDirectory.resolve(entry.name)

            // There is no guarantee that directory entries appear before file entries, so ensure that the parent
            // directory for a file exists.
            target.parentFile.safeMkdirs()

            target.outputStream().use { output ->
                input.copyTo(output)
            }

            copyExecutableModeBit(target, mode(entry))
        }

        if (!processed) throw IOException("Unsupported archive format or empty archive.")
    }

/**
 * Copy the executable bit contained in [mode] to the [target] file's mode bits.
 */
private fun copyExecutableModeBit(target: File, mode: Int) {
    if (Os.isWindows) return

    // Note: In contrast to Java, Kotlin does not support octal literals, see
    // https://kotlinlang.org/docs/reference/basic-types.html#literal-constants.
    // The bit-triplets from left to right stand for user, groups, other, respectively.
    if (mode and 0b001_000_001 != 0) {
        target.setExecutable(true, (mode and 0b000_000_001) == 0)
    }
}

/**
 * Pack the file into a ZIP [targetFile] using [Deflater.BEST_COMPRESSION]. If the file is a directory its content is
 * recursively added to the archive. Only regular files are added, e.g. symbolic links or directories are skipped. If
 * a [prefix] is specified, it is added to the file names in the ZIP file. If not all directories or files shall be
 * added to the archive a [directoryFilter] or [fileFilter] can be provided. By default, [VCS directories]
 * [VCS_DIRECTORIES] are skipped. Return the [targetFile] for convenience.
 */
fun File.packZip(
    targetFile: File,
    prefix: String = "",
    overwrite: Boolean = false,
    directoryFilter: (File) -> Boolean = { it.name !in VCS_DIRECTORIES },
    fileFilter: (File) -> Boolean = { true }
): File {
    require(overwrite || !targetFile.exists()) {
        "The target ZIP file '${targetFile.absolutePath}' must not exist."
    }

    ZipArchiveOutputStream(targetFile).use { output ->
        output.setLevel(Deflater.BEST_COMPRESSION)

        walkTopDown().onEnter {
            Files.isDirectory(it.toPath(), LinkOption.NOFOLLOW_LINKS) && directoryFilter(it)
        }.filter {
            Files.isRegularFile(it.toPath(), LinkOption.NOFOLLOW_LINKS) && fileFilter(it) && it != targetFile
        }.forEach { file ->
            val packPath = prefix + file.toRelativeString(this)
            val entry = ZipArchiveEntry(file, packPath)
            output.putArchiveEntry(entry)
            file.inputStream().use { input -> input.copyTo(output) }
            output.closeArchiveEntry()
        }
    }

    return targetFile
}
