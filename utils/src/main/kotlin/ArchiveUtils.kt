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
import java.io.IOException
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

private val UNCOMPRESSED_EXTENSIONS = listOf(".pom")
private val TAR_EXTENSIONS = listOf(".gem", ".tar", ".tar.gz", ".tgz", ".tar.bz2", ".tbz2")
private val ZIP_EXTENSIONS = listOf(".aar", ".egg", ".jar", ".war", ".whl", ".zip")
private val SEVENZIP_EXTENSIONS = listOf(".7z")

val ARCHIVE_EXTENSIONS = TAR_EXTENSIONS + ZIP_EXTENSIONS + SEVENZIP_EXTENSIONS

fun File.unpack(targetDirectory: File) {
    val lowerName = name.toLowerCase()
    when {
        UNCOMPRESSED_EXTENSIONS.any { lowerName.endsWith(it) } -> {
        }
        TAR_EXTENSIONS.any { lowerName.endsWith(it) } -> unpackTar(targetDirectory)
        ZIP_EXTENSIONS.any { lowerName.endsWith(it) } -> unpackZip(targetDirectory)
        SEVENZIP_EXTENSIONS.any { lowerName.endsWith(it) } -> unpack7Zip(targetDirectory)
        else -> throw IOException("Unknown archive type for file '$absolutePath'.")
    }
}

/**
 * Unpack the file assuming that it is a tape archive (tar). This implementation ignores empty directories and symbolic
 * links.
 *
 * @param targetDirectory The target directory to store the unpacked content of this archive.
 */
fun File.unpackTar(targetDirectory: File) {
    val inputStream = when (extension.toLowerCase()) {
        "gz", "tgz" -> GzipCompressorInputStream(inputStream())
        "bz2", "tbz2" -> BZip2CompressorInputStream(inputStream())
        "gem", "tar" -> inputStream()
        else -> throw IOException("Unknown compression scheme for tar file '$absolutePath'.")
    }

    TarArchiveInputStream(inputStream).use {
        while (true) {
            val entry = it.nextTarEntry ?: break

            if (!entry.isFile) {
                continue
            }

            val target = File(targetDirectory, entry.name)

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
 * Unpack the file assuming that it is a zip file. This implementation ignores empty directories and symbolic links.
 *
 * @param targetDirectory The target directory to store the unpacked content of this archive.
 */
fun File.unpackZip(targetDirectory: File) {
    ZipArchiveInputStream(inputStream()).use {
        while (true) {
            val entry = it.nextZipEntry ?: break

            if (entry.isDirectory || entry.isUnixSymlink) {
                continue
            }

            val target = File(targetDirectory, entry.name)

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

fun File.unpack7Zip(targetDirectory: File) {
    SevenZFile(this).use {
        while (true) {
            val entry = it.nextEntry ?: break

            if (entry.isDirectory || entry.isAntiItem) {
                continue
            }

            val target = File(targetDirectory, entry.name)

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
 * Pack the file into a zip file. If it is a directory its content is recursively added to the zip file. The compression
 * level used is [Deflater.BEST_COMPRESSION]. Only regular files are added, for example symbolic links or directories
 * are skipped.
 *
 * @param targetFile The target zip file, must not exist.
 * @param prefix A prefix to add to the file names in the zip file.
 */
fun File.packZip(targetFile: File, prefix: String = "") {
    require(!targetFile.exists()) {
        "The target zip file '${targetFile.absolutePath}' must not exist."
    }

    ZipArchiveOutputStream(targetFile).use { output ->
        output.setLevel(Deflater.BEST_COMPRESSION)
        Files.walkFileTree(toPath(), object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (attrs.isRegularFile) {
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
