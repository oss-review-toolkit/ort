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

import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream

enum class ArchiveType(vararg val extensions: String) {
    TAR(".gem", ".tar"),
    TAR_BZIP2(".tar.bz2", ".tbz2"),
    TAR_GZIP(".crate", ".tar.gz", ".tgz"),
    TAR_XZ(".tar.xz", ".txz"),
    ZIP(".aar", ".egg", ".jar", ".war", ".whl", ".zip"),
    SEVENZIP(".7z"),
    POM(".pom"), // Special case of a "fake archive".
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
    if (ArchiveType.getType(name) == ArchiveType.SEVENZIP) {
        unpack7Zip(targetDirectory)
    } else {
        inputStream().unpack(name, targetDirectory)
    }

/**
 * Unpack the [InputStream] to [targetDirectory]. The compression scheme is guessed from the [filename].
 */
fun InputStream.unpack(filename: String, targetDirectory: File) {
    when (ArchiveType.getType(filename)) {
        ArchiveType.TAR -> unpackTar(targetDirectory)
        ArchiveType.TAR_BZIP2 -> BZip2CompressorInputStream(this).unpackTar(targetDirectory)
        ArchiveType.TAR_GZIP -> GzipCompressorInputStream(this).unpackTar(targetDirectory)
        ArchiveType.TAR_XZ -> XZCompressorInputStream(this).unpackTar(targetDirectory)
        ArchiveType.ZIP -> unpackZip(targetDirectory)
        ArchiveType.SEVENZIP -> {
            throw IOException("Cannot unpack a 7-Zip archive from an InputStream, use a File instead.")
        }
        ArchiveType.POM -> use {
            // Special case, copy the POM to the target directory.
            File(targetDirectory, filename).outputStream().use { copyTo(it) }
        }
        ArchiveType.NONE -> {
            throw IOException("Unable to guess compression scheme from file name '$filename'.")
        }
    }
}

/**
 * Unpack the [InputStream] to [targetDirectory] assuming that it is a tape archive (TAR). This implementation ignores
 * empty directories and symbolic links.
 */
fun InputStream.unpackTar(targetDirectory: File) {
    TarArchiveInputStream(this).use {
        while (true) {
            val entry = it.nextTarEntry ?: break

            if (!entry.isFile) {
                continue
            }

            val target = File(targetDirectory, entry.name)

            // There is no guarantee that directory entries appear before file entries, so always ensure the parent
            // directory for a file exists.
            target.parentFile.safeMkdirs()

            target.outputStream().use { output ->
                it.copyTo(output)
            }

            if (!Os.isWindows) {
                // Note: In contrast to Java, Kotlin does not support octal literals, see
                // https://kotlinlang.org/docs/reference/basic-types.html#literal-constants.
                // The bit-triplets from left to right stand for user, groups, other, respectively.
                if (entry.mode and 0b001_000_001 != 0) {
                    target.setExecutable(true, (entry.mode and 0b000_000_001) == 0)
                }
            }
        }
    }
}

/**
 * Unpack the [InputStream] to [targetDirectory] assuming that it is a ZIP file. This implementation ignores empty
 * directories and symbolic links.
 */
fun InputStream.unpackZip(targetDirectory: File) {
    ZipArchiveInputStream(this).use {
        while (true) {
            val entry = it.nextZipEntry ?: break

            if (entry.isDirectory || entry.isUnixSymlink) {
                continue
            }

            val target = File(targetDirectory, entry.name)

            // There is no guarantee that directory entries appear before file entries, so always ensure the parent
            // directory for a file exists.
            target.parentFile.safeMkdirs()

            target.outputStream().use { output ->
                it.copyTo(output)
            }

            if (!Os.isWindows) {
                // Note: In contrast to Java, Kotlin does not support octal literals, see
                // https://kotlinlang.org/docs/reference/basic-types.html#literal-constants.
                // The bit-triplets from left to right stand for user, groups, other, respectively.
                if (entry.unixMode and 0b001_000_001 != 0) {
                    target.setExecutable(true, (entry.unixMode and 0b000_000_001) == 0)
                }
            }
        }
    }
}

/**
 * Unpack the [File] assuming it is a 7-Zip archive. This implementation ignores empty directories and symbolic links.
 */
fun File.unpack7Zip(targetDirectory: File) {
    SevenZFile(this).use {
        while (true) {
            val entry = it.nextEntry ?: break

            if (entry.isDirectory || entry.isAntiItem) {
                continue
            }

            val target = File(targetDirectory, entry.name)

            // There is no guarantee that directory entries appear before file entries, so always ensure the parent
            // directory for a file exists.
            target.parentFile.safeMkdirs()

            target.outputStream().use { output ->
                val buffer = ByteArray(entry.size.toInt())
                it.read(buffer)
                output.write(buffer)
            }
        }
    }
}

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
