/*
 * Copyright (c) 2017 HERE Europe B.V.
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

package com.here.ort.downloader

import java.io.File
import java.io.IOException

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream

private val TAR_GZ_EXTENSIONS = listOf(".tar.gz", ".tgz")
private val ZIP_EXTENSIONS = listOf("jar", "war", "zip")

fun File.unpack(targetDirectory: File) {
    when {
        TAR_GZ_EXTENSIONS.any { this.name.toLowerCase().endsWith(it) } -> unpackTarGz(targetDirectory)
        ZIP_EXTENSIONS.any { this.name.toLowerCase().endsWith(it) } -> unpackZip(targetDirectory)
        else -> throw IOException("Unknown archive type for file '$absolutePath'.")
    }
}

/**
 * Unpack the file assuming that it is a tar gz file. This implementation ignores empty directories and symbolic links.
 *
 * @param targetDirectory The target directory to store the unpacked content of this archive.
 */
fun File.unpackTarGz(targetDirectory: File) {
    val gzipInputStream = GzipCompressorInputStream(inputStream())
    val tarInputStream = TarArchiveInputStream(gzipInputStream)
    tarInputStream.use {
        while (true) {
            val entry = it.nextTarEntry ?: break

            if (entry.isDirectory || entry.isSymbolicLink) {
                continue
            }

            val target = File(targetDirectory, entry.name)

            if (!target.createParentDir()) {
                throw IOException("Could not create directory '${target.parentFile.absolutePath}'.")
            }

            target.outputStream().use { output ->
                it.copyTo(output)
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
    val zipInputStream = ZipArchiveInputStream(inputStream())
    zipInputStream.use {
        while (true) {
            val entry = it.nextZipEntry ?: break

            if (entry.isDirectory || entry.isUnixSymlink) {
                continue
            }

            val target = File(targetDirectory, entry.name)

            if (!target.createParentDir()) {
                throw IOException("Could not create directory '${target.parentFile.absolutePath}'.")
            }

            target.outputStream().use { output ->
                it.copyTo(output)
            }
        }
    }
}

/**
 * Create the parent directory of this file if it does not exist.
 *
 * @return true if the parent directory could be created or if it already exists and is a directory.
 */
fun File.createParentDir(): Boolean {
    if (!parentFile.exists()) {
        return parentFile.mkdirs()
    }

    return parentFile.isDirectory
}
