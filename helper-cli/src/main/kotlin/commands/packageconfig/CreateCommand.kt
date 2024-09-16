/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import com.github.ajalt.clikt.core.UsageError
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.file

import java.io.File

import org.ossreviewtoolkit.helper.utils.OrtHelperCommand
import org.ossreviewtoolkit.helper.utils.PathExcludeGenerator
import org.ossreviewtoolkit.helper.utils.sortPathExcludes
import org.ossreviewtoolkit.helper.utils.write
import org.ossreviewtoolkit.model.ArtifactProvenance
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.model.config.VcsMatcher
import org.ossreviewtoolkit.model.licenses.LicenseClassifications
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.scanner.storages.PackageBasedFileStorage
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.common.safeMkdirs
import org.ossreviewtoolkit.utils.ort.storage.LocalFileStorage
import org.ossreviewtoolkit.utils.spdx.SpdxSingleLicenseExpression

internal class CreateCommand : OrtHelperCommand(
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

    private val generatePathExcludes by option(
        "--generate-path-excludes",
        help = "Generate path excludes."
    ).flag()

    private val licenseClassificationsFile by option(
        "--license-classifications-file", "-i",
        help = "The license classifications file."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }

    private val nonOffendingLicenseCategories by option(
        "--non-offending-license-categories",
        help = "Specify licenses by their category which should be considered non-offending. Path excludes are not" +
            "generated for files or directories which only contain non-offending licenses. Each category name " +
            "must be present in the given license classifications file."
    ).split(",").default(emptyList())

    private val nonOffendingLicenseIds by option(
        "--non-offending-license-ids",
        help = "Specify license IDs which should be considered non-offending. Path excludes are not generated for " +
            "files or directories which only contain non-offending licenses."
    ).split(",").default(emptyList())

    private val noSkeletonFiles by option(
        "--no-skeleton-files",
        help = "Only write the package configuration if it contains path excludes or license finding curations."
    ).flag()

    override fun run() {
        outputDir.safeMkdirs()

        val scanResultsStorage = PackageBasedFileStorage(LocalFileStorage(scanResultsStorageDir))
        val scanResults = scanResultsStorage.readForId(id = packageId).getOrThrow().run {
            listOfNotNull(
                find { it.provenance is RepositoryProvenance },
                find { it.provenance is ArtifactProvenance }
            )
        }

        scanResults.forEach { scanResult ->
            createPackageConfiguration(scanResult).writeToFile()
        }
    }

    private fun PackageConfiguration.writeToFile() {
        val filename = if (vcs != null) "vcs.yml" else "source-artifact.yml"
        val outputFile = getOutputFile(filename)

        if (!forceOverwrite && outputFile.exists()) {
            throw UsageError("The output file '${outputFile.absolutePath}' must not exist yet.", statusCode = 2)
        }

        if (pathExcludes.isEmpty() && noSkeletonFiles) {
            println("Skip writing empty package configuration to '${outputFile.absolutePath}'.")
        } else {
            write(outputFile)
            println("Wrote a package configuration to '${outputFile.absolutePath}'.")
        }
    }

    private fun getOutputFile(filename: String): File {
        val relativeOutputFilePath = if (createHierarchicalDirs) {
            "${packageId.toPath(emptyValue = "_")}/$filename"
        } else {
            filename
        }

        return outputDir.resolve(relativeOutputFilePath)
    }

    private fun createPackageConfiguration(scanResult: ScanResult): PackageConfiguration =
        PackageConfiguration(
            id = packageId,
            sourceArtifactUrl = (scanResult.provenance as? ArtifactProvenance)?.sourceArtifact?.url,
            vcs = (scanResult.provenance as? RepositoryProvenance)?.let {
                VcsMatcher(
                    type = it.vcsInfo.type,
                    url = it.vcsInfo.url,
                    revision = it.resolvedRevision
                )
            },
            pathExcludes = if (generatePathExcludes) {
                PathExcludeGenerator.generatePathExcludes(scanResult.getFindingPaths()).sortPathExcludes()
            } else {
                emptyList()
            }
        )

    private fun ScanResult.getFindingPaths(): Set<String> =
        buildSet {
            val nonOffendingLicenses = getNonOffendingLicenses()

            summary.licenseFindings.filter {
                nonOffendingLicenses.isEmpty() || !nonOffendingLicenses.containsAll(it.license.decompose())
            }.mapTo(this) { it.location.path }

            summary.copyrightFindings.filter {
                nonOffendingLicenses.isEmpty()
            }.mapTo(this) { it.location.path }
        }

    private fun getNonOffendingLicenses(): Set<SpdxSingleLicenseExpression> {
        val result = mutableSetOf<SpdxSingleLicenseExpression>()

        // Filter blanks to allow passing an empty list as argument to simplify caller code.
        nonOffendingLicenseIds.filterNot { it.isBlank() }.mapTo(result) {
            SpdxSingleLicenseExpression.parse(it)
        }

        // Filter blanks to allow passing an empty list as argument to simplify caller code.
        if (nonOffendingLicenseCategories.all { it.isBlank() }) return result

        val licenseClassifications = licenseClassificationsFile?.readValue<LicenseClassifications>()
            ?: throw UsageError(
                message = "The license classifications file must be specified in order to resolve the given " +
                    "non-offending license category names to license IDs.",
                statusCode = 2
            )

        nonOffendingLicenseCategories.flatMapTo(result) { categoryName ->
            @Suppress("UnsafeCallOnNullableType")
            licenseClassifications.licensesByCategory[categoryName]
                ?: throw UsageError(
                    message = "The given license category '$categoryName' was not found in " +
                        "'${licenseClassificationsFile!!.absolutePath}'.",
                    statusCode = 2
                )
        }

        return result
    }
}
