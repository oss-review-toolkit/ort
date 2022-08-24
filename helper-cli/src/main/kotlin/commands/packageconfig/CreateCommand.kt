/*
 * Copyright (C) 2020-2021 HERE Europe B.V.
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

package org.ossreviewtoolkit.helper.commands.packageconfig

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import java.io.File

import org.ossreviewtoolkit.helper.utils.write
import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.model.config.VcsMatcher
import org.ossreviewtoolkit.scanner.storages.FileBasedStorage
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.ort.storage.LocalFileStorage

internal class CreateCommand : CliktCommand(
    help = "Creates one package configuration for the source artifact scan and one for the VCS scan, if " +
            "a corresponding scan result exists in the given ORT result for the respective provenance. The output " +
            "package configuration YAML files are written to the given output directory."
) {
    private val scanResultsStorageDir by option(
        "--scan-results-storage-dir",
        help = "The scan results storage to read the scan results from."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val packageId by option(
        "--package-id",
        help = "The target package for which the package configuration shall be generated."
    ).convert { Identifier(it) }
        .required()

    private val outputDir by option(
        "--output-dir",
        help = "The output directory to write the package configurations to."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val createHierarchicalDirs by option(
        "--create-hierarchical-dirs",
        help = "Place the output YAML files in the directory '\$outputdir/\$type/\$namespace/\$name/\$version'."
    ).flag()

    private val forceOverwrite by option(
        "--force-overwrite",
        help = "Overwrite any output files if they already exist."
    ).flag()

    override fun run() {
        outputDir.safeMkdirs()

        val scanResultsStorage = FileBasedStorage(LocalFileStorage(scanResultsStorageDir))
        val scanResults = scanResultsStorage.read(packageId).getOrDefault(emptyList()).run {
            listOfNotNull(
                find { it.provenance is RepositoryProvenance },
                find { it.provenance is ArtifactProvenance }
            )
        }

        scanResults.forEach { scanResult ->
            createPackageConfiguration(scanResult.provenance).writeToFile()
        }
    }

    private fun PackageConfiguration.writeToFile() {
        val filename = if (vcs != null) "vcs.yml" else "source-artifact.yml"
        val outputFile = getOutputFile(filename)

        if (!forceOverwrite && outputFile.exists()) {
            throw UsageError("The output file '${outputFile.absolutePath}' must not exist yet.", statusCode = 2)
        }

        write(outputFile)
        println("Wrote a package configuration to '${outputFile.absolutePath}'.")
    }

    private fun getOutputFile(filename: String): File {
        val relativeOutputFilePath = if (createHierarchicalDirs) {
            "${packageId.toPath(emptyValue = "_")}/$filename"
        } else {
            filename
        }

        return outputDir.resolve(relativeOutputFilePath)
    }

    private fun createPackageConfiguration(provenance: Provenance): PackageConfiguration =
        PackageConfiguration(
            id = packageId,
            sourceArtifactUrl = provenance.let { it as? ArtifactProvenance }?.let {
                it.sourceArtifact.url
            },
            vcs = provenance.let { it as? RepositoryProvenance }?.let {
                VcsMatcher(
                    type = it.vcsInfo.type,
                    url = it.vcsInfo.url,
                    revision = it.resolvedRevision
                )
            }
        )
}
