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
import java.io.InputStream
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
private val TAR_EXTENSIONS = listOf(".gem", ".tar")
private val TAR_BZIP2_EXTENSIONS = listOf(".tar.bz2", ".tbz2")
private val TAR_GZIP_EXTENSIONS = listOf(".crate", ".tar.gz", ".tgz")
private val ZIP_EXTENSIONS = listOf(".aar", ".egg", ".jar", ".war", ".whl", ".zip")
private val SEVENZIP_EXTENSIONS = listOf(".7z")

val ARCHIVE_EXTENSIONS = TAR_EXTENSIONS + TAR_BZIP2_EXTENSIONS + TAR_GZIP_EXTENSIONS + ZIP_EXTENSIONS +
        SEVENZIP_EXTENSIONS

/**
 * Unpack the [File] to [targetDirectory].
 */
fun File.unpack(targetDirectory: File) {
    val lowerName = name.toLowerCase()
    if (SEVENZIP_EXTENSIONS.any { lowerName.endsWith(it) }) {
        unpack7Zip(targetDirectory)
    } else {
        inputStream().unpack(name, targetDirectory)
    }
}

/**
 * Unpack the [InputStream] to [targetDirectory]. The compression scheme is guessed from the [filename].
 */
fun InputStream.unpack(filename: String, targetDirectory: File) {
    val lowerName = filename.toLowerCase()
    when {
        UNCOMPRESSED_EXTENSIONS.any { lowerName.endsWith(it) } -> {
            use { File(targetDirectory, filename).outputStream().use { copyTo(it) } }
        }

        TAR_BZIP2_EXTENSIONS.any { lowerName.endsWith(it) } -> {
            BZip2CompressorInputStream(this).unpackTar(targetDirectory)
        }

        TAR_GZIP_EXTENSIONS.any { lowerName.endsWith(it) } -> {
            GzipCompressorInputStream(this).unpackTar(targetDirectory)
        }

        TAR_EXTENSIONS.any { lowerName.endsWith(it) } -> unpackTar(targetDirectory)

        ZIP_EXTENSIONS.any { lowerName.endsWith(it) } -> unpackZip(targetDirectory)

        else -> {
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
 */
fun File.packZip(targetFile: File, prefix: String = "") {
    require(!targetFile.exists()) {
        "The target ZIP file '${targetFile.absolutePath}' must not exist."
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
