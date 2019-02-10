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

package com.here.ort.scanner.scanners

import ch.frankel.slf4k.*

import com.fasterxml.jackson.databind.JsonNode

import com.here.ort.model.EMPTY_JSON_NODE
import com.here.ort.model.LicenseFinding
import com.here.ort.model.Provenance
import com.here.ort.model.ScanResult
import com.here.ort.model.ScanSummary
import com.here.ort.model.config.ScannerConfiguration
import com.here.ort.model.yamlMapper
import com.here.ort.scanner.AbstractScannerFactory
import com.here.ort.scanner.HTTP_CACHE_PATH
import com.here.ort.scanner.LocalScanner
import com.here.ort.scanner.ScanException
import com.here.ort.utils.CommandLineTool
import com.here.ort.utils.OS
import com.here.ort.utils.OkHttpClientHelper
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.log

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.time.Instant

import okhttp3.Request

import okio.Okio

class Askalono(name: String, config: ScannerConfiguration) : LocalScanner(name, config) {
    class Factory : AbstractScannerFactory<Askalono>("Askalono") {
        override fun create(config: ScannerConfiguration) = Askalono(scannerName, config)
    }

    override val scannerVersion = "0.3.0"
    override val resultFileExt = "txt"

    override fun command(workingDir: File?): String {
        val extension = when {
            OS.isLinux -> "linux"
            OS.isMac -> "osx"
            OS.isWindows -> "exe"
            else -> throw IllegalArgumentException("Unsupported operating system.")
        }

        return "askalono.$extension"
    }

    override fun getVersion(dir: File): String {
        // Create a temporary tool to get its version from the installation in a specific directory.
        val cmd = command()
        val tool = object : CommandLineTool {
            override fun command(workingDir: File?) = dir.resolve(cmd).absolutePath
        }

        return tool.getVersion(transform = {
            // "askalono --version" returns a string like "askalono 0.2.0-beta.1", so simply remove the prefix.
            it.substringAfter("askalono ")
        })
    }

    override fun bootstrap(): File {
        val scannerExe = command()
        val url = "https://github.com/amzn/askalono/releases/download/$scannerVersion/$scannerExe"

        log.info { "Downloading $scannerName from $url... " }

        val request = Request.Builder().get().url(url).build()

        return OkHttpClientHelper.execute(HTTP_CACHE_PATH, request).use { response ->
            val body = response.body()

            if (response.code() != HttpURLConnection.HTTP_OK || body == null) {
                throw IOException("Failed to download $scannerName from $url.")
            }

            if (response.cacheResponse() != null) {
                log.info { "Retrieved $scannerName from local cache." }
            }

            val scannerDir = createTempDir("ort", "$scannerName-$scannerVersion").apply { deleteOnExit() }

            val scannerFile = File(scannerDir, scannerExe)
            Okio.buffer(Okio.sink(scannerFile)).use { it.writeAll(body.source()) }

            if (!OS.isWindows) {
                // Ensure the executable Unix mode bit to be set.
                scannerFile.setExecutable(true)
            }

            scannerDir
        }
    }

    override fun getConfiguration() = ""

    override fun scanPath(path: File, resultsFile: File): ScanResult {
        val startTime = Instant.now()

        val process = ProcessCapture(
                scannerPath.absolutePath,
                "crawl", path.absolutePath
        )

        val endTime = Instant.now()

        if (process.stderr.isNotBlank()) {
            log.debug { process.stderr }
        }

        with(process) {
            if (isSuccess) {
                stdoutFile.copyTo(resultsFile)
                val result = getResult(resultsFile)
                val summary = generateSummary(startTime, endTime, result)
                return ScanResult(Provenance(), getDetails(), summary, result)
            } else {
                throw ScanException(errorMessage)
            }
        }
    }

    override fun getResult(resultsFile: File): JsonNode {
        if (!resultsFile.isFile || resultsFile.length() == 0L) return EMPTY_JSON_NODE

        val yamlNodes = resultsFile.readLines().chunked(3) { (path, license, score) ->
            val licenseNoOriginalText = license.substringBeforeLast(" (original text)")
            val yamlString = listOf("Path: $path", licenseNoOriginalText, score).joinToString("\n")
            yamlMapper.readTree(yamlString)
        }

        return yamlMapper.createArrayNode().apply { addAll(yamlNodes) }
    }

    override fun generateSummary(startTime: Instant, endTime: Instant, result: JsonNode): ScanSummary {
        val findings = result.map {
            val license = getSpdxLicenseIdString(it["License"].textValue())
            LicenseFinding(license, sortedSetOf(), sortedSetOf())
        }.toSortedSet()

        return ScanSummary(startTime, endTime, result.size(), findings, errors = mutableListOf())
    }
}
