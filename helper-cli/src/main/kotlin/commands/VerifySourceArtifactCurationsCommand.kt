/*
 * Copyright (C) 2019 HERE Europe B.V.
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

package com.here.ort.helper.commands

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters
import com.beust.jcommander.converters.FileConverter

import com.here.ort.CommandWithHelp
import com.here.ort.helper.common.download
import com.here.ort.model.PackageCuration
import com.here.ort.model.readValue
import com.here.ort.utils.PARAMETER_ORDER_MANDATORY
import com.here.ort.utils.collectMessagesAsString
import com.here.ort.utils.hash

import java.io.File
import java.io.IOException

@Parameters(
    commandNames = ["verify-source-artifact-curations"],
    commandDescription = "Verifies that all curated source artifacts can be downloaded and that the hashes are correct."
)
internal class VerifySourceArtifactCurationsCommand : CommandWithHelp() {
    @Parameter(
        description = "A YAML file that contains package curation data.",
        required = true,
        order = PARAMETER_ORDER_MANDATORY,
        converter = FileConverter::class
    )
    private lateinit var packageCurationsFile: File

    override fun runCommand(jc: JCommander): Int {
        val curations = packageCurationsFile.readValue<List<PackageCuration>>()

        val failed = curations.filterNot { curation ->
            curation.data.sourceArtifact?.let { sourceArtifact ->
                println("\n-----")
                println("Checking source artifact for ${curation.id.toCoordinates()}.")
                println("Downloading ${sourceArtifact.url}.")

                try {
                    val file = download(sourceArtifact.url)
                    val hash = file.hash(sourceArtifact.hash.algorithm.toString())

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
                    val message = "Failed to download source artifact: ${e.collectMessagesAsString()}"
                    println(message)
                    false
                }
            } ?: true
        }

        println("\n-----")
        return if (failed.isEmpty()) {
            println("Successfully verified all source artifact curations.")
            0
        } else {
            println(
                "Source artifact curations for the following packages could NOT be verified, check the log for details:"
            )
            println(failed.joinToString(separator = "\n") { it.id.toCoordinates() })
            1
        }
    }
}
