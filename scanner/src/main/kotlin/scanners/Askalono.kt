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

import com.here.ort.scanner.Scanner
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.log
import com.here.ort.utils.yamlMapper

import java.io.File

/**
 * Global variable that gets set by a command line parameter parsed in the main entry points of the modules.
 */
var askalonoExecutablePath = ""

data class AskalonoResult(
        val filePath: String,
        val output: String,
        val error: String
)

object Askalono : Scanner() {
    override val resultFileExtension = "yml"

    private val possibleLicenseFiles = listOf("license", "copying", "readme")

    override fun scanPath(path: File, resultsFile: File): Result {
        println("Running Askalono in directory '${path.absolutePath}'")

        val allResults = mutableSetOf<AskalonoResult>()

        if (path.isDirectory) {
            path.walkTopDown().filter { file ->
                possibleLicenseFiles.any { licenseFileNameTemplate ->
                    file.name.contains(licenseFileNameTemplate, true)
                }
            }.forEach { licenseFile ->
                        val licenseFilePath = licenseFile.toRelativeString(path)
                        allResults.add(runAskalano(path.absoluteFile, licenseFilePath))
                    }
        } else {
            allResults.add(runAskalano(targetFile = path.absolutePath))
        }

        yamlMapper.writerWithDefaultPrettyPrinter().writeValue(resultsFile, allResults)
        println("Stored Askalono results in '${resultsFile.absolutePath}'.")
        return getResult(allResults)
    }

    private fun runAskalano(workDir: File? = null, targetFile: String): AskalonoResult {
        println("Running Askalono for file '$targetFile'...")
        val process = ProcessCapture(
                workDir,
                askalonoExecutablePath,
                "id",
                targetFile
        )

        with(process) {
            if (stderr().isNotBlank()) {
                log.debug { process.stderr() }
            }

            return AskalonoResult(targetFile, stdout(), stderr())
        }
    }

    override fun getResult(resultsFile: File): Result {
        val valueType = yamlMapper.typeFactory.constructCollectionLikeType(Set::class.java, AskalonoResult::class.java)
        val resultsMap: Set<AskalonoResult> = yamlMapper.readValue(resultsFile, valueType)

        return getResult(resultsMap)
    }

    private fun getResult(results: Set<AskalonoResult>): Result {
        val licenses = sortedSetOf<String>()
        val errors = sortedSetOf<String>()
        results.forEach { result ->
            if (result.error.isNotBlank()) {
                errors.add(result.error)
            }

            if (result.output.isNotBlank()) {
                licenses.add(result.output.lines().first().substringAfter("License:").trim())
            }
        }

        return Result(licenses, errors)
    }
}
