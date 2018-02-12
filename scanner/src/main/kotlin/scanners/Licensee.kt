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

import com.here.ort.scanner.ScanException
import com.here.ort.scanner.Scanner
import com.here.ort.utils.OS
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.yamlMapper
import com.here.ort.utils.log

import java.io.File

object Licensee : Scanner() {
    override val scannerExe = if (OS.isWindows) "licensee.bat" else "licensee"
    override val resultFileExt = "yml"

    // Licensee cannot report its version before https://github.com/benbalter/licensee/pull/269 which is not contained
    // in any release yet.
    override fun getVersion(executable: String) = "9.8.0"

    override fun scanPath(path: File, resultsFile: File): Result {
        val process = ProcessCapture(
                path.parentFile,
                scannerExe,
                path.name
        )

        if (process.stderr().isNotBlank()) {
            log.debug { process.stderr() }
        }

        with(process) {
            if (exitValue() == 0) {
                stdoutFile.copyTo(resultsFile)
                return getResult(resultsFile)
            } else {
                throw ScanException(failMessage)
            }
        }
    }

    override fun getResult(resultsFile: File): Result {
        val licenses = sortedSetOf<String>()
        val errors = sortedSetOf<String>()

        if (resultsFile.isFile && resultsFile.length() > 0) {
            // Convert Licensee's output for "Closest licenses" (in case of non-exact matches) into proper YAML
            // by replacing the asterisk with a dash.
            val yamlResults = resultsFile.readText().replace("    * ", "    - ")
            val scanOutput = yamlMapper.readTree(yamlResults)
            val matchedFiles = scanOutput["Matched files"].asIterable().map { it.asText() }
            matchedFiles.forEach {
                licenses.add(scanOutput[it]["License"].asText())
            }
        }

        return Result(licenses, errors)
    }
}
