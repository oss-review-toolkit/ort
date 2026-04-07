/*
 * Copyright (C) 2025 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.spdx

import java.io.File

import kotlin.jvm.optionals.getOrDefault
import kotlin.jvm.optionals.getOrNull

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManagerResult
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.Includes
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor

import org.spdx.library.SpdxModelFactory
import org.spdx.library.model.v3_0_1.build.Build
import org.spdx.library.model.v3_0_1.core.Element
import org.spdx.library.model.v3_0_1.core.Relationship
import org.spdx.library.model.v3_0_1.core.RelationshipType
import org.spdx.library.model.v3_0_1.core.SpdxDocument
import org.spdx.library.model.v3_0_1.software.Sbom
import org.spdx.library.model.v3_0_1.software.SpdxPackage as SpdxPackage
import org.spdx.storage.simple.InMemSpdxStore
import org.spdx.v3jsonldstore.JsonLDStore
import kotlin.jvm.optionals.getOrDefault

private const val PROJECT_TYPE = "SPDX"
internal const val PACKAGE_TYPE_SPDX = "SPDX"

@OrtPlugin(
    displayName = "SPDX",
    description = "A package manager that uses SPDX version 3 documents as definition files.",
    factory = PackageManagerFactory::class
)
class Spdx(override val descriptor: PluginDescriptor = SpdxFactory.descriptor) : PackageManager(PROJECT_TYPE) {
    init {
        SpdxModelFactory.init()
    }

    private lateinit var dependencyHandler: SpdxDependencyHandler
    private val graphBuilder by lazy { DependencyGraphBuilder(dependencyHandler) }

    override val globsForDefinitionFiles = listOf("*.spdx.json", "*.spdx.jsonld")

    override fun resolveDependencies(
        analysisRoot: File,
        definitionFile: File,
        excludes: Excludes,
        includes: Includes,
        analyzerConfig: AnalyzerConfiguration,
        labels: Map<String, String>
    ): List<ProjectAnalyzerResult> {
        val spdxDocument = parseSpdx3File(definitionFile)

        logger.debug {
            val counts = spdxDocument.elements.groupingBy { it.type }.eachCount().toSortedMap()

            counts.entries.joinToString("\n", prefix = "Found ${spdxDocument.elements.size} SPDX element(s):\n") {
                "\t${it.key}: ${it.value}"
            }
        }

        val relationships = mutableListOf<Relationship>()
        val buildRecipes = mutableListOf<Build>()

        // Only iterate over elements once to get all needed instance types.
        spdxDocument.elements.forEach {
            when (it) {
                is Relationship -> relationships += it
                is Build -> buildRecipes += it
            }
        }

        dependencyHandler = SpdxDependencyHandler(relationships)

        val documentDescribes = spdxDocument.rootElements +
            dependencyHandler.getElementsReferredFrom(spdxDocument, RelationshipType.DESCRIBES)

        val fallbackProjectName = getFallbackProjectName(analysisRoot, definitionFile)

        val projectIds = documentDescribes.mapNotNull { describedElement ->
            when (describedElement) {
                is Sbom -> {
                    val rootPackages = describedElement.rootElements
                        .filterIsInstanceTo<SpdxPackage, MutableSet<SpdxPackage>>(mutableSetOf())

                    val diff = describedElement.rootElements - rootPackages
                    if (diff.isNotEmpty()) {
                        logger.info {
                            "The following root elements are no packages and will be disregarded: " +
                                diff.joinToString { "${it.name.getOrDefault("unnamed")} (${it.type})" }
                        }
                    }

                    rootPackages.map { pkg ->
                        val projectName = pkg.name.getOrNull()
                            ?: describedElement.name.getOrNull()
                            ?: spdxDocument.name.getOrNull()
                            ?: fallbackProjectName

                        Identifier(
                            type = projectType,
                            namespace = "",
                            name = projectName,
                            version = ""
                        )
                    }
                }

                else -> {
                    logger.info { "Unhandled root element type '${describedElement.type}'. " }
                    null
                }
            }
        }.flatten()

        // Yocto builds packages from source, so build recipes define the dependency relationship between packages.
        val rootRecipes = buildRecipes.filter {
            // Root packages of a scope are not declared explicitly, they are implied by not being depended on.
            dependencyHandler.getElementsReferringTo(it, RelationshipType.DEPENDS_ON).isEmpty()
        }

        return projectIds.map { projectId ->
            val outputs = rootRecipes.flatMap { it.getElementsRecursive(RelationshipType.HAS_OUTPUT) }

            val installDependencies = outputs.filterIsInstanceTo<SpdxPackage, MutableSet<SpdxPackage>>(mutableSetOf())

            val diff = outputs - installDependencies
            if (diff.isNotEmpty()) {
                logger.info {
                    "The following output elements are no packages and will be disregarded: " +
                        diff.joinToString { "${it.name.getOrDefault("unnamed")} (${it.type})" }
                }
            }

            graphBuilder.addDependencies(projectId, "install", installDependencies)

            val sourceDependencies = rootRecipes.flatMap { it.getElementsRecursive(RelationshipType.HAS_INPUT) }

            graphBuilder.addDependencies(projectId, "source", sourceDependencies.filterIsInstance<SpdxPackage>())

            val project = Project(
                id = projectId,
                definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
                authors = emptySet(),
                declaredLicenses = emptySet(),
                // Do not derive the (processed) VCS information from the working directory as done for several other
                // package managers, including SpdxDocumentFile, because the location of the SPDX file usually does not
                // match the location of the project it describes.
                vcs = VcsInfo.EMPTY,
                homepageUrl = "",
                scopeNames = graphBuilder.scopesFor(projectId)
            )

            ProjectAnalyzerResult(project, packages = emptySet())
        }
    }

    private fun Build.getElementsRecursive(vararg types: RelationshipType): Set<Element> =
        dependencyHandler.getElementsReferredFrom(this, *types) +
            dependencyHandler.getElementsReferredFrom(this, RelationshipType.DEPENDS_ON).filterIsInstance<Build>()
                .flatMap { it.getElementsRecursive(*types) }

    override fun createPackageManagerResult(projectResults: Map<File, List<ProjectAnalyzerResult>>) =
        PackageManagerResult(projectResults, graphBuilder.build(), graphBuilder.packages())
}

private fun parseSpdx3File(spdxFile: File): SpdxDocument {
    val baseStore = InMemSpdxStore()
    val store = JsonLDStore(baseStore)
    return spdxFile.inputStream().use { store.deSerialize(it, /* overwrite = */ false) }
}
