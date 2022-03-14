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

@file:Suppress("MatchingDeclarationName")

package org.ossreviewtoolkit.utils

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.zip.Deflater

import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.ArchiveInputStream
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
    TAR(".gem", ".tar"),
    TAR_BZIP2(".tar.bz2", ".tbz2"),
    TAR_GZIP(".crate", ".tar.gz", ".tgz"),
    TAR_XZ(".tar.xz", ".txz"),
    ZIP(".aar", ".egg", ".jar", ".war", ".whl", ".zip"),
    SEVENZIP(".7z"),
    NONE("");

    companion object {
        fun getType(filename: String): ArchiveType {
            val lowerName = filename.toLowerCase()
            return (enumValues<ArchiveType>().asList() - NONE).find { type ->
                type.extensions.any { lowerName.endsWith(it) }
            } ?: NONE
        }
    }
}

/**
 * Unpack the [File] to [targetDirectory].
 */
fun File.unpack(targetDirectory: File) =
    when (ArchiveType.getType(name)) {
        ArchiveType.SEVENZIP -> unpack7Zip(targetDirectory)
        ArchiveType.ZIP -> unpackZip(targetDirectory)

        ArchiveType.TAR -> inputStream().unpackTar(targetDirectory)
        ArchiveType.TAR_BZIP2 -> BZip2CompressorInputStream(inputStream()).unpackTar(targetDirectory)
        ArchiveType.TAR_GZIP -> GzipCompressorInputStream(inputStream()).unpackTar(targetDirectory)
        ArchiveType.TAR_XZ -> XZCompressorInputStream(inputStream()).unpackTar(targetDirectory)

        ArchiveType.NONE -> {
            throw IOException("Unable to guess compression scheme from file name '$name'.")
        }
    }

/**
 * Unpack the [File] assuming it is a 7-Zip archive. This implementation ignores empty directories and symbolic links.
 */
fun File.unpack7Zip(targetDirectory: File) {
    SevenZFile(this).use { zipFile ->
        while (true) {
            val entry = zipFile.nextEntry ?: break

            if (entry.isDirectory || entry.isAntiItem) {
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
 * Unpack the [File] assuming it is a Zip archive.
 */
fun File.unpackZip(targetDirectory: File) = ZipFile(this).unpack(targetDirectory)

/**
 * Unpack the [ByteArray] assuming it is a Zip archive.
 */
fun ByteArray.unpackZip(targetDirectory: File) = ZipFile(SeekableInMemoryByteChannel(this)).unpack(targetDirectory)

/**
 * Pack the file into a ZIP [targetFile] using [Deflater.BEST_COMPRESSION]. If the file is a directory its content is
 * recursively added to the archive. Only regular files are added, e.g. symbolic links or directories are skipped. If
 * a [prefix] is specified, it is added to the file names in the ZIP file.
 * If not all files shall be added to the archive a [filter] can be provided.
 */
fun File.packZip(
    targetFile: File,
    prefix: String = "",
    overwrite: Boolean = false,
    filter: (Path) -> Boolean = { true }
) {
    require(overwrite || !targetFile.exists()) {
        "The target ZIP file '${targetFile.absolutePath}' must not exist."
    }

    ZipArchiveOutputStream(targetFile).use { output ->
        output.setLevel(Deflater.BEST_COMPRESSION)
        Files.walkFileTree(toPath(), object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (attrs.isRegularFile && filter(file)) {
                    val entry = ZipArchiveEntry(file.toFile(), "$prefix${this@packZip.toPath().relativize(file)}")
                    output.putArchiveEntry(entry)
                    file.toFile().inputStream().use { input -> input.copyTo(output) }
                    output.closeArchiveEntry()
                }

                return FileVisitResult.CONTINUE
            }
        })
    }
}

/**
 * Unpack the [InputStream] to [targetDirectory] assuming that it is a tape archive (TAR). This implementation ignores
 * empty directories and symbolic links.
 */
fun InputStream.unpackTar(targetDirectory: File) =
    TarArchiveInputStream(this).unpack(
        targetDirectory,
        { entry -> !(entry as TarArchiveEntry).isFile },
        { entry -> (entry as TarArchiveEntry).mode }
    )

/**
 * Unpack the [InputStream] to [targetDirectory] assuming that it is a ZIP archive. This implementation ignores empty
 * directories and symbolic links.
 */
fun InputStream.unpackZip(targetDirectory: File) =
    ZipArchiveInputStream(this).unpack(
        targetDirectory,
        { entry -> (entry as ZipArchiveEntry).let { it.isDirectory || it.isUnixSymlink } },
        { entry -> (entry as ZipArchiveEntry).unixMode }
    )

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
 * Unpack this [ArchiveInputStream] to the [targetDirectory], skipping all entries for which [shouldSkip] returns true,
 * and using what [mode] returns as the file mode bits.
 */
private fun ArchiveInputStream.unpack(
    targetDirectory: File,
    shouldSkip: (ArchiveEntry) -> Boolean,
    mode: (ArchiveEntry) -> Int
) =
    use { input ->
        while (true) {
            val entry = input.nextEntry ?: break

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
    }

/**
 * Unpack the [ZipFile]. In contrast to [InputStream.unpackZip] this properly parses the ZIP's central directory, see
 * https://commons.apache.org/proper/commons-compress/zip.html#ZipArchiveInputStream_vs_ZipFile.
 */
private fun ZipFile.unpack(targetDirectory: File) =
    use { zipFile ->
        val entries = zipFile.entries

        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()

            if (entry.isDirectory || entry.isUnixSymlink) {
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
