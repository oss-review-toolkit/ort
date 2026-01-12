/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.yoctospdx

import java.io.File
import java.util.Optional

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.utils.ort.DeclaredLicenseProcessor
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.toExpression
import org.ossreviewtoolkit.utils.spdx.toSpdx

import org.spdx.library.SpdxModelFactory
import org.spdx.library.model.v3_0_1.core.Element
import org.spdx.library.model.v3_0_1.core.HashAlgorithm as SpdxHashAlgorithm
import org.spdx.library.model.v3_0_1.core.Relationship
import org.spdx.library.model.v3_0_1.core.RelationshipType
import org.spdx.library.model.v3_0_1.core.SpdxDocument
import org.spdx.library.model.v3_0_1.simplelicensing.LicenseExpression
import org.spdx.library.model.v3_0_1.software.SpdxPackage as Spdx3Package
import org.spdx.storage.simple.InMemSpdxStore
import org.spdx.v3jsonldstore.JsonLDDeserializer

import com.fasterxml.jackson.databind.ObjectMapper

private fun <T> Optional<T>.getOrNull(): T? = if (isPresent) get() else null

/**
 * A package manager plugin that uses SPDX 3 documents created by Yocto as definition files.
 */
@OrtPlugin(
    displayName = "YoctoSpdx",
    description = "A package manager that uses SPDX 3 documents created by Yocto as definition files.",
    factory = PackageManagerFactory::class
)
class YoctoSpdx(override val descriptor: PluginDescriptor = YoctoSpdxFactory.descriptor) :
    PackageManager("YoctoSpdx") {

    override val globsForDefinitionFiles = listOf("*.spdx.json")

    override fun resolveDependencies(
        analysisRoot: File,
        definitionFile: File,
        excludes: Excludes,
        analyzerConfig: AnalyzerConfiguration,
        labels: Map<String, String>
    ): List<ProjectAnalyzerResult> {
        logger.debug { "Parsing SPDX 3 document from '$definitionFile'..." }

        val (spdxDocument, allElements) = parseSpdx3File(definitionFile)

        // Find all software packages
        val spdxPackages = allElements.filterIsInstance<Spdx3Package>()
        logger.debug { "Found ${spdxPackages.size} SPDX packages in the document." }

        // Find relationships for license resolution
        val relationships = allElements.filterIsInstance<Relationship>()

        // Build license map from relationships
        val licenseMap = buildLicenseMap(relationships, allElements)

        // Group packages by purpose/scope
        val packagesByScope = groupPackagesByScope(spdxPackages)

        // Convert SPDX packages to ORT packages
        val ortPackages = mutableMapOf<Identifier, Package>()
        val scopes = mutableSetOf<Scope>()

        packagesByScope.forEach { (scopeName, packages) ->
            val packageRefs = packages.mapNotNull { spdxPkg ->
                val ortPackage = spdxPkg.toOrtPackage(licenseMap)
                if (ortPackage != null) {
                    ortPackages.merge(ortPackage.id, ortPackage) { existing, new ->
                        mergePackages(existing, new)
                    }
                    PackageReference(ortPackage.id)
                } else {
                    null
                }
            }.toSet()

            if (packageRefs.isNotEmpty()) {
                scopes += Scope(name = scopeName, dependencies = packageRefs)
            }
        }

        // Create project from the document itself
        val projectName = spdxDocument?.getName()?.getOrNull()?.toString() ?: definitionFile.nameWithoutExtension
        val project = Project(
            id = Identifier(
                type = projectType,
                namespace = "",
                name = projectName,
                version = ""
            ),
            definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
            authors = emptySet(),
            declaredLicenses = emptySet(),
            vcs = VcsInfo.EMPTY,
            homepageUrl = "",
            scopeDependencies = scopes
        )

        logger.debug {
            "Resolved ${ortPackages.size} packages in ${scopes.size} scopes: " +
                scopes.joinToString { "${it.name} (${it.dependencies.size})" }
        }

        return listOf(ProjectAnalyzerResult(project, ortPackages.values.toSet()))
    }

    private fun mergePackages(existing: Package, new: Package): Package {
        val mergedDeclaredLicenses = existing.declaredLicenses + new.declaredLicenses
        val mergedConcludedLicense = setOfNotNull(
            existing.concludedLicense,
            new.concludedLicense
        ).toExpression()

        return existing.copy(
            declaredLicenses = mergedDeclaredLicenses,
            declaredLicensesProcessed = DeclaredLicenseProcessor.process(mergedDeclaredLicenses),
            concludedLicense = mergedConcludedLicense
        )
    }

    private fun parseSpdx3File(file: File): Pair<SpdxDocument?, List<Element>> {
        SpdxModelFactory.init()

        val store = InMemSpdxStore()
        val allElements = mutableListOf<Element>()
        var spdxDocument: SpdxDocument? = null

        logger.debug { "Deserializing SPDX file using JsonLDDeserializer..." }

        val mapper = ObjectMapper()
        val root = mapper.readTree(file)

        val deserializer = JsonLDDeserializer(store)

        val typedValues = if (root.has("@graph")) {
            deserializer.deserializeGraph(root.get("@graph"))
        } else {
            listOf(deserializer.deserializeElement(root))
        }

        logger.debug { "Deserialization complete, inflating ${typedValues.size} objects..." }

        for (typedValue in typedValues) {
            try {
                val obj = SpdxModelFactory.inflateModelObject(
                    store,
                    typedValue.objectUri,
                    typedValue.type,
                    null,
                    typedValue.specVersion,
                    false,
                    ""
                )

                if (obj is Element) {
                    allElements += obj

                    if (obj is SpdxDocument) {
                        spdxDocument = obj
                    }
                }
            } catch (e: Exception) {
                logger.debug { "Could not inflate object ${typedValue.objectUri} (type: ${typedValue.type}): ${e.message}" }
            }
        }

        logger.debug { "Parsed ${allElements.size} elements from SPDX file" }

        return spdxDocument to allElements
    }

    private fun buildLicenseMap(
        relationships: List<Relationship>,
        allElements: List<Element>
    ): Map<String, String> {
        val licenseMap = mutableMapOf<String, String>()

        // Find hasConcludedLicense relationships
        for (rel in relationships) {
            val relType = rel.getRelationshipType()
            if (relType != RelationshipType.HAS_CONCLUDED_LICENSE) continue

            val fromElement = rel.getFrom() ?: continue
            val fromId = fromElement.getObjectUri()

            val toElements = rel.getTos()
            if (toElements.isEmpty()) continue

            // Find the license expression from the target
            val targetElement = toElements.firstOrNull() ?: continue
            val targetId = targetElement.getObjectUri()
            val licenseElement = allElements.find { it.getObjectUri() == targetId }

            if (licenseElement is LicenseExpression) {
                val expression = licenseElement.getLicenseExpression()?.toString()
                if (expression != null) {
                    licenseMap[fromId] = expression
                }
            }
        }

        return licenseMap
    }

    private fun groupPackagesByScope(packages: List<Spdx3Package>): Map<String, List<Spdx3Package>> {
        return packages.groupBy { pkg ->
            pkg.getPrimaryPurpose().getOrNull()?.name?.lowercase() ?: "other"
        }
    }

    private fun Spdx3Package.toOrtPackage(licenseMap: Map<String, String>): Package? {
        val pkgName = getName().getOrNull()?.toString() ?: return null
        val pkgVersion = getPackageVersion().getOrNull()?.toString() ?: ""

        // Extract CPE from external identifiers
        val cpe = getExternalIdentifiers()
            .filter { extId ->
                val idType = extId.getExternalIdentifierType()
                idType?.toString()?.lowercase() == "cpe23"
            }
            .mapNotNull { extId -> extId.getIdentifier()?.toString() }
            .firstOrNull()

        // Get download location and hash
        val downloadLoc = getDownloadLocation().getOrNull()?.toString()
        val sourceArtifact = if (!downloadLoc.isNullOrBlank()) {
            val hash = getVerifiedUsings()
                .filterIsInstance<org.spdx.library.model.v3_0_1.core.Hash>()
                .firstOrNull()
                ?.let { spdxHash ->
                    val algorithm = spdxHash.getAlgorithm()
                    val value = spdxHash.getHashValue()?.toString()
                    if (algorithm != null && value != null) {
                        val ortAlgorithm = when (algorithm) {
                            SpdxHashAlgorithm.SHA256 -> HashAlgorithm.SHA256
                            SpdxHashAlgorithm.SHA1 -> HashAlgorithm.SHA1
                            SpdxHashAlgorithm.SHA512 -> HashAlgorithm.SHA512
                            SpdxHashAlgorithm.MD5 -> HashAlgorithm.MD5
                            else -> HashAlgorithm.UNKNOWN
                        }
                        Hash(value, ortAlgorithm)
                    } else {
                        Hash.NONE
                    }
                } ?: Hash.NONE

            RemoteArtifact(downloadLoc, hash)
        } else {
            RemoteArtifact.EMPTY
        }

        // Get description
        val descriptionText = getSummary().getOrNull()?.toString()
            ?: getDescription().getOrNull()?.toString() ?: ""

        // Get homepage
        val homepage = getHomePage().getOrNull()?.toString() ?: ""

        // Get license from the license map
        val licenseExpression = licenseMap[getObjectUri()]
        val declaredLicenses = if (licenseExpression != null) setOf(licenseExpression) else emptySet()
        val concludedLicense = licenseExpression?.toSpdxOrNull()

        return Package(
            id = Identifier(
                type = projectType,
                namespace = "",
                name = pkgName,
                version = pkgVersion
            ),
            cpe = cpe,
            authors = emptySet(),
            declaredLicenses = declaredLicenses,
            concludedLicense = concludedLicense,
            description = descriptionText,
            homepageUrl = homepage,
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = sourceArtifact,
            vcs = VcsInfo.EMPTY
        )
    }
}

private fun String.toSpdxOrNull(): SpdxExpression? =
    runCatching { toSpdx() }.getOrNull()
