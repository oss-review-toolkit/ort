/*
 * Copyright (C) 2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.helper.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.types.file

import java.io.IOException

import org.ossreviewtoolkit.model.PackageCuration
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.ort.OkHttpClientHelper
import org.ossreviewtoolkit.utils.ort.createOrtTempDir

internal class VerifySourceArtifactCurationsCommand : CliktCommand(
    help = "Verifies that all curated source artifacts can be downloaded and that the hashes are correct."
) {
    private val packageCurationsFile by argument(
        "package-curations-file",
        help = "A file containing package curation data."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }

    override fun run() {
        val curations = packageCurationsFile.readValue<List<PackageCuration>>()

        val failed = curations.filterNot { curation ->
            curation.data.sourceArtifact?.let { sourceArtifact ->
                println("\n-----")
                println("Checking source artifact for ${curation.id.toCoordinates()}.")
                println("Downloading ${sourceArtifact.url}.")

                val tempDir = createOrtTempDir()

                try {
                    val file = OkHttpClientHelper.downloadFile(sourceArtifact.url, tempDir).getOrThrow()
                    val hash = sourceArtifact.hash.algorithm.calculate(file)

                    println("Expected hash: ${sourceArtifact.hash.algorithm} ${sourceArtifact.hash.value}")
                    println("Actual hash  : ${sourceArtifact.hash.algorithm} $hash")

                    if (!sourceArtifact.hash.verify(file)) {
                        val message = "Hashes do NOT match!"
                        println(message)
                        false
                    } else {
                        true
                    }
                } catch (e: IOException) {
                    val message = "Failed to download source artifact: ${e.collectMessages()}"
                    println(message)
                    false
                } finally {
                    tempDir.safeDeleteRecursively(force = true)
                }
            } ?: true
        }

        println("\n-----")
        if (failed.isNotEmpty()) {
            val message = buildString {
                append("Source artifact curations for the following packages could NOT be verified, ")
                appendLine("check the log for details:")
                appendLine(failed.joinToString(separator = "\n") { it.id.toCoordinates() })
            }

            println(message)
            throw ProgramResult(1)
        }

        println("Successfully verified all source artifact curations.")
    }
}
