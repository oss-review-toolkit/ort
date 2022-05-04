/*
 * Copyright (C) 2021 Porsche AG
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

package org.ossreviewtoolkit.reporter.reporters

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.json.JsonMapper

import java.io.File
import java.util.LinkedList
import java.util.Locale
import java.util.SortedSet
import java.util.TreeSet
import java.util.stream.Collectors

import org.ossreviewtoolkit.model.CuratedPackage
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.licenses.LicenseInfoResolver
import org.ossreviewtoolkit.model.licenses.LicenseView
import org.ossreviewtoolkit.model.licenses.ResolvedCopyright
import org.ossreviewtoolkit.model.licenses.ResolvedCopyrightFinding
import org.ossreviewtoolkit.reporter.LicenseTextProvider
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput

class AOSD2JSONReporter : Reporter {
    override val reporterName = "AOSD2JSON"
    private var reportFileName = "aosd-2-report.json"

    override fun generateReport(input: ReporterInput, outputDir: File, options: Map<String, String>): List<File> {
        val combinedDirectDependencies = TreeSet<String>()
        val combinedDependencies = LinkedList<AOSD2Dependency>()

        for (project in input.ortResult.getProjects()) {
            combinedDirectDependencies.addAll(project.collectDependencies(1).map { it.toCoordinates() })

            val knownCoordinates = combinedDependencies.stream().map { it.id }.collect(Collectors.toSet())

            val tmpDependency = getLicenseFindings(input, project).values.stream()
                .filter({ !knownCoordinates.contains(it.id) })
                .toList()

            combinedDependencies.addAll(tmpDependency)
        }

        val export = AOSD2Export(
            directDependencies = combinedDirectDependencies,
            dependencies = combinedDependencies
        )

        val mapper = JsonMapper()
        mapper.propertyNamingStrategy = PropertyNamingStrategies.LOWER_CAMEL_CASE
        val licenseModelJson = mapper.writer().writeValueAsString(export)

        val outputFile = outputDir.resolve(reportFileName)
        outputFile.bufferedWriter().use { it.write(licenseModelJson) }

        return listOf(outputFile)
    }

    private fun getLicenseFindings(input: ReporterInput, project: Project): Map<Identifier, AOSD2Dependency> =
        input.ortResult.collectDependencies(project.id)
            .filter { !input.ortResult.isExcluded(it) }
            .associateWith { id ->
                // We know that a package exists for the reference.
                val pkg = input.ortResult.getPackage(id)!!
                val dependencies = input.ortResult.collectDependencies(pkg.pkg.id, 1).map { it.toCoordinates() }

                AOSD2Dependency(
                    id.toCoordinates(),
                    id.nameWithOptionalNamespace(),
                    id.version,
                    id.version,
                    pkg.pkg.vcsProcessed.url,
                    pkg.toAOSD2Licenses(input.licenseInfoResolver, input.licenseTextProvider),
                    listOf(AOSD2Part()),
                    deployPackage = (
                            pkg.pkg.sourceArtifact.takeUnless { it == RemoteArtifact.EMPTY }
                                ?: pkg.pkg.binaryArtifact
                            ).aosd2DeployPackage(),
                    dependencies
                )
            }
}

private fun Identifier.nameWithOptionalNamespace(): String = if (namespace.isEmpty()) name else listOf(namespace, name)
    .joinToString("/")

private fun RemoteArtifact.aosd2DeployPackage(): AOSD2DeployPackage = AOSD2DeployPackage(
    "default",
    AOSD2Checksum("${hash.algorithm.name}-${hash.value}"), url
)

private fun CuratedPackage.toAOSD2Licenses(
    licenseInfoResolver: LicenseInfoResolver,
    licenseTextProvider: LicenseTextProvider
): List<AOSD2License> {
    fun isAuthorOnlyCopyright(resolvedCopyright: ResolvedCopyright): Boolean {
        fun isDefindeByTextLocation(findings: ResolvedCopyrightFinding) =
            findings.location != AOSDExcelReporter.UNDEFINED_TEXT_LOCATION

        return resolvedCopyright.findings.filter { isDefindeByTextLocation(it) }.count() > 0
    }

    val licenseInfo = licenseInfoResolver.resolveLicenseInfo(pkg.id).filterExcluded()
    val licenses = licenseInfo.filter(LicenseView.CONCLUDED_OR_DETECTED).filterExcluded()

    return licenses.map { license ->
        val copyrights = license.getResolvedCopyrights()
            .filter { isAuthorOnlyCopyright(it) }
            .mapTo(mutableSetOf()) { it.statement }

        AOSD2License(
            license.license.simpleLicense(),
            AOSD2Copyright(
                copyrights,
                String.format(
                    Locale.ENGLISH,
                    "Author: %s", pkg.authors
                )
            ),
            licenseTextProvider.getLicenseText(license.license.simpleLicense()).orEmpty()
        )
    }
}

data class AOSD2Export(
    @JsonProperty("\$schema")
    val schema: String = "./aosd.schema.json",
    val directDependencies: SortedSet<String>,
    val dependencies: List<AOSD2Dependency>
)

data class AOSD2Dependency(
    val id: String,
    val name: String,
    val version: String,
    val versionRange: String,
    var scmUrl: String,
    val licenses: List<AOSD2License> = emptyList(),
    val parts: List<AOSD2Part>,
    val deployPackage: AOSD2DeployPackage,
    val externalDependencies: List<String>
)

data class AOSD2License(
    val spdxId: String,
    val copyrights: AOSD2Copyright,
    val text: String,
    val origin: String = "packagemanagement"
)

data class AOSD2Part(
    val name: String = "default",
    val providers: List<AOSD2PartProvider> = listOf(AOSD2PartProvider())
)

data class AOSD2PartProvider(
    val usage: String = "dynamic_linking",
    val modified: Boolean = false
)

data class AOSD2DeployPackage(
    val name: String,
    val checksums: AOSD2Checksum,
    val downloadUrl: String
)

data class AOSD2Checksum(
    val integrity: String
)

data class AOSD2Copyright(
    val holders: Set<String>,
    val notice: String
)
