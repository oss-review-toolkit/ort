/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.clients.dos

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.ossreviewtoolkit.clients.dos.DOSService.Companion.logger
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Zip a directory to scan, to be sent to S3 Object Storage as one zipped file.
 */
fun File.packZip(zipFile: File) {
    val pathPrefix = this.absolutePath.length + 1 // to exclude the initial "/"
    ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
        this.walk().forEach { file ->
            if (!Files.isSymbolicLink(file.toPath()) && !file.isDirectory) {
                FileInputStream(file).use { fis ->
                    val entryName = file.absolutePath.substring(pathPrefix)
                    //logger.info { "Adding entry: $entryName" }
                    try {
                        val entry = ZipEntry(entryName)
                        zos.putNextEntry(entry)
                        fis.copyTo(zos)
                        zos.closeEntry()
                    } catch (e: Exception) {
                        logger.error { "Failed to add entry: $entryName" }
                        e.printStackTrace()
                    }
                }
            }
        }
    }
}
