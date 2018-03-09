/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

package com.here.ort.scanner.scanners

import ch.frankel.slf4k.*

import com.here.ort.scanner.LocalScanner
import com.here.ort.scanner.Main
import com.here.ort.scanner.ScanException
import com.here.ort.utils.OkHttpClientHelper
import com.here.ort.utils.OS
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.getCommandVersion
import com.here.ort.utils.jsonMapper
import com.here.ort.utils.log
import com.here.ort.utils.unpack

import okhttp3.Request

import okio.Okio

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection

object BoyterLc : LocalScanner() {
    const val VERSION = "1.3.1"

    override val scannerExe = if (OS.isWindows) "lc.exe" else "lc"
    override val resultFileExt = "json"

    override fun bootstrap(): File? {
        val url = if (OS.isWindows) {
            "https://github.com/boyter/lc/releases/download/v$VERSION/lc-$VERSION-x86_64-pc-windows.zip"
        } else {
            "https://github.com/boyter/lc/releases/download/v$VERSION/lc-$VERSION-x86_64-unknown-linux.zip"
        }

        log.info { "Downloading $this from '$url'... " }

        val request = Request.Builder().get().url(url).build()

        return OkHttpClientHelper.execute(Main.HTTP_CACHE_PATH, request).use { response ->
            val body = response.body()

            if (response.code() != HttpURLConnection.HTTP_OK || body == null) {
                throw IOException("Failed to download $this from $url.")
            }

            if (response.cacheResponse() != null) {
                log.info { "Retrieved $this from local cache." }
            }

            val scannerArchive = createTempFile(suffix = url.substringAfterLast("/"))
            Okio.buffer(Okio.sink(scannerArchive)).use { it.writeAll(body.source()) }

            val unpackDir = createTempDir()
            unpackDir.deleteOnExit()

            log.info { "Unpacking '$scannerArchive' to '$unpackDir'... " }
            scannerArchive.unpack(unpackDir)

            if (!OS.isWindows) {
                // The Linux version is distributed as a ZIP, but our ZIP unpacker seems to be unable to properly handle
                // Unix mode bits.
                File(unpackDir, scannerExe).setExecutable(true)
            }

            unpackDir
        }
    }

    override fun getVersion(executable: String) =
            getCommandVersion(scannerPath.absolutePath, transform = {
                // "lc --version" returns a string like "licensechecker version 1.1.1", so simply remove the prefix.
                it.substringAfter("licensechecker version ")
            })

    override fun scanPath(path: File, resultsFile: File): Result {
        val process = ProcessCapture(
                scannerPath.absolutePath,
                "--confidence", "0.95", // Cut-off value to only get most relevant matches.
                "--format", "json",
                "--output", resultsFile.absolutePath,
                path.absolutePath
        )

        if (process.stderr().isNotBlank()) {
            log.debug { process.stderr() }
        }

        with(process) {
            if (isSuccess()) {
                return getResult(resultsFile)
            } else {
                throw ScanException(failMessage)
            }
        }
    }

    override fun getResult(resultsFile: File): Result {
        var fileCount = 0
        val licenses = sortedSetOf<String>()
        val errors = sortedSetOf<String>()

        if (resultsFile.isFile && resultsFile.length() > 0) {
            val json = jsonMapper.readTree(resultsFile)

            fileCount = json.count()

            json.forEach { file ->
                licenses.addAll(file["LicenseGuesses"].map { license ->
                    license["LicenseId"].asText()
                })
            }
        }

        return Result(fileCount, licenses, errors)
    }
}
