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

import com.here.ort.model.EMPTY_JSON_NODE
import com.here.ort.model.Provenance
import com.here.ort.model.ScanResult
import com.here.ort.model.ScanSummary
import com.here.ort.model.ScannerDetails
import com.here.ort.model.jsonMapper
import com.here.ort.scanner.LocalScanner
import com.here.ort.scanner.Main
import com.here.ort.scanner.ScanException
import com.here.ort.utils.OkHttpClientHelper
import com.here.ort.utils.OS
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.getCommandVersion
import com.here.ort.utils.log
import com.here.ort.utils.unpack

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.time.Instant

import okhttp3.Request

import okio.Okio

object BoyterLc : LocalScanner() {
    override val scannerExe = if (OS.isWindows) "lc.exe" else "lc"
    override val scannerVersion = "1.3.1"
    override val resultFileExt = "json"

    val CONFIGURATION_OPTIONS = listOf(
            "--confidence", "0.95", // Cut-off value to only get most relevant matches.
            "--format", "json"
    )

    override fun bootstrap(): File {
        val platform = when {
            OS.isMac -> "x86_64-apple-darwin"
            OS.isWindows -> "x86_64-pc-windows"
            else -> "x86_64-unknown-linux"
        }

        val url = "https://github.com/boyter/lc/releases/download/v$scannerVersion/lc-$scannerVersion-$platform.zip"

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

    override fun getConfiguration() = CONFIGURATION_OPTIONS.joinToString(" ")

    override fun getVersion() =
            getCommandVersion(scannerPath.absolutePath, transform = {
                // "lc --version" returns a string like "licensechecker version 1.1.1", so simply remove the prefix.
                it.substringAfter("licensechecker version ")
            })

    override fun scanPath(path: File, resultsFile: File, provenance: Provenance, scannerDetails: ScannerDetails)
            : ScanResult {
        val startTime = Instant.now()

        val process = ProcessCapture(
                scannerPath.absolutePath,
                *CONFIGURATION_OPTIONS.toTypedArray(),
                "--output", resultsFile.absolutePath,
                path.absolutePath
        )

        val endTime = Instant.now()

        if (process.stderr().isNotBlank()) {
            log.debug { process.stderr() }
        }

        with(process) {
            if (isSuccess()) {
                val result = getResult(resultsFile)
                val summary = ScanSummary(startTime, endTime, result.fileCount, result.licenses, result.errors)
                return ScanResult(provenance, scannerDetails, summary, result.rawResult)
            } else {
                throw ScanException(failMessage)
            }
        }
    }

    override fun getResult(resultsFile: File): Result {
        var fileCount = 0
        val licenses = sortedSetOf<String>()
        val errors = sortedSetOf<String>()

        val json = if (resultsFile.isFile && resultsFile.length() > 0) {
            jsonMapper.readTree(resultsFile).also {
                fileCount = it.count()

                it.forEach { file ->
                    licenses.addAll(file["LicenseGuesses"].map { license ->
                        license["LicenseId"].asText()
                    })
                }
            }
        } else {
            EMPTY_JSON_NODE
        }

        return Result(fileCount, licenses, errors, json)
    }
}
