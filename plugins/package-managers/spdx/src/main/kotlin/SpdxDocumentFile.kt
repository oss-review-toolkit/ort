/*
 * Copyright (C) 2020 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerDependency
import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManagerResult
import org.ossreviewtoolkit.analyzer.determineEnabledPackageManagers
import org.ossreviewtoolkit.analyzer.toPackageReference
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.Includes
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.utils.toIdentifier
import org.ossreviewtoolkit.model.utils.toPackageUrl
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.plugins.api.PluginConfig
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.packagemanagers.spdx.utils.SpdxDocumentCache
import org.ossreviewtoolkit.plugins.packagemanagers.spdx.utils.SpdxResolvedDocument
import org.ossreviewtoolkit.plugins.packagemanagers.spdx.utils.extractScopeFromExternalReferences
import org.ossreviewtoolkit.plugins.packagemanagers.spdx.utils.isExternalDocumentReferenceId
import org.ossreviewtoolkit.plugins.packagemanagers.spdx.utils.locateCpe
import org.ossreviewtoolkit.plugins.packagemanagers.spdx.utils.locateExternalReference
import org.ossreviewtoolkit.plugins.packagemanagers.spdx.utils.mapNotPresentToEmpty
import org.ossreviewtoolkit.plugins.packagemanagers.spdx.utils.projectPackage
import org.ossreviewtoolkit.plugins.packagemanagers.spdx.utils.toIdentifier
import org.ossreviewtoolkit.plugins.packagemanagers.spdx.utils.toPackage
import org.ossreviewtoolkit.plugins.packagemanagers.spdx.utils.wrapPresentInSet
import org.ossreviewtoolkit.utils.spdxdocument.model.SpdxExternalReference
import org.ossreviewtoolkit.utils.spdxdocument.model.SpdxPackage
import org.ossreviewtoolkit.utils.spdxdocument.model.SpdxRelationship

private const val PROJECT_TYPE = "SpdxDocumentFile"
internal const val PACKAGE_TYPE_SPDX = "SpdxDocumentFile"

private const val DEFAULT_SCOPE_NAME = "default"

private val SPDX_LINKAGE_RELATIONSHIPS = mapOf(
    SpdxRelationship.Type.DYNAMIC_LINK to PackageLinkage.DYNAMIC,
    SpdxRelationship.Type.STATIC_LINK to PackageLinkage.STATIC
)

private val SPDX_SCOPE_RELATIONSHIPS = SpdxRelationship.Type.entries.filter { it.name.endsWith("_DEPENDENCY_OF") }

data class SpdxDocumentFileConfig(
    /**
     * If this option is enabled and an SPDX package has a PURL as an external reference, the ORT [Package]'s
     * [Identifier] is deduced from that PURL instead of from the [SpdxPackage]'s [ID][SpdxPackage.spdxId].
     */
    @OrtPluginOption(defaultValue = "false")
    val deduceOrtIdFromPurl: Boolean
)

/**
 * A "fake" package manager implementation that uses SPDX documents as definition files to declare projects and describe
 * packages. See https://github.com/spdx/spdx-spec/issues/439 for details.
 */
@OrtPlugin(
    displayName = "SpdxDocumentFile",
    description = "A package manager that uses SPDX documents as definition files.",
    factory = PackageManagerFactory::class
)
class SpdxDocumentFile(
    override val descriptor: PluginDescriptor = SpdxDocumentFileFactory.descriptor,
    private val config: SpdxDocumentFileConfig
) :
    PackageManager(PROJECT_TYPE) {
    override val globsForDefinitionFiles = listOf("*.spdx.yml", "*.spdx.yaml", "*.spdx.json")

    private val spdxDocumentCache = SpdxDocumentCache()

    /**
     * Return the dependencies of the package with the given [pkgId] defined in [doc] of the
     * [SpdxRelationship.Type.DEPENDENCY_OF] type. Identified dependencies are mapped to ORT [Package]s and then
     * added to [packages]. The [ancestorIds] set contains the IDs of the package that have already been encountered;
     * it is used to detect circular dependencies.
     */
    private fun getDependencies(
        pkgId: String,
        doc: SpdxResolvedDocument,
        packages: MutableSet<Package>,
        ancestorIds: MutableSet<String>,
        analyzerConfig: AnalyzerConfiguration
    ): Set<PackageReference> {
        logger.debug { "Retrieving dependencies for package '$pkgId'." }

        if (!ancestorIds.add(pkgId)) {
            logger.warn { "A cycle was detected in the dependencies for packages $ancestorIds." }
            return emptySet()
        }

        return getDependencies(
            pkgId,
            doc,
            packages,
            ancestorIds,
            SpdxRelationship.Type.DEPENDENCY_OF,
            analyzerConfig
        ) { target ->
            val issues = mutableListOf<Issue>()
            getPackageManagerDependency(target, doc, analyzerConfig) ?: doc.getSpdxPackageForId(target, issues)
                ?.let { dependency ->
                    val targetFile = doc.getDefinitionFile(target)
                    val ortPackage = dependency.toPackage(targetFile, doc, config.deduceOrtIdFromPurl)
                    packages += ortPackage

                    PackageReference(
                        id = ortPackage.id,
                        dependencies = getDependencies(target, doc, packages, ancestorIds, analyzerConfig),
                        linkage = getLinkageForDependency(dependency, pkgId, doc.relationships),
                        issues = issues
                    )
                }
        }.also { ancestorIds.remove(pkgId) }
    }

    /**
     * Return the dependencies of the package with the given [pkgId] defined in [doc] of the given
     * [dependencyOfRelation] type. Optionally, the [SpdxRelationship.Type.DEPENDS_ON] type is handled by
     * [dependsOnCase]. Identified dependencies are mapped to ORT [Package]s and then added to [packages].
     * Use the given [ancestorIds] set to detect cyclic dependencies.
     */
    private fun getDependencies(
        pkgId: String,
        doc: SpdxResolvedDocument,
        packages: MutableSet<Package>,
        ancestorIds: MutableSet<String>,
        dependencyOfRelation: SpdxRelationship.Type,
        analyzerConfig: AnalyzerConfiguration,
        dependsOnCase: (String) -> PackageReference? = { null }
    ): Set<PackageReference> =
        doc.relationships.mapNotNullTo(mutableSetOf()) { (source, relation, target, _) ->
            val issues = mutableListOf<Issue>()

            val isDependsOnRelation = relation == SpdxRelationship.Type.DEPENDS_ON || hasDefaultScopeLinkage(
                source, target, relation, doc.relationships
            )

            when {
                // Dependencies can either be defined on the target...
                pkgId.equals(target, ignoreCase = true) && relation == dependencyOfRelation -> {
                    if (pkgId != target) {
                        issues += createAndLogIssue("Source '$pkgId' has to match target '$target' case-sensitively.")
                    }

                    getPackageManagerDependency(source, doc, analyzerConfig) ?: doc.getSpdxPackageForId(source, issues)
                        ?.let { dependency ->
                            val sourceFile = doc.getDefinitionFile(source)
                            val ortPackage = dependency.toPackage(sourceFile, doc, config.deduceOrtIdFromPurl)
                            packages += ortPackage

                            PackageReference(
                                id = ortPackage.id,
                                dependencies = getDependencies(source, doc, packages, ancestorIds, analyzerConfig),
                                issues = issues,
                                linkage = getLinkageForDependency(dependency, target, doc.relationships)
                            )
                        }
                }

                // ...or on the source.
                pkgId.equals(source, ignoreCase = true) && isDependsOnRelation -> {
                    if (pkgId != source) {
                        issues += createAndLogIssue("Source '$source' has to match target '$pkgId' case-sensitively.")
                    }

                    val pkgRef = dependsOnCase(target)
                    pkgRef?.copy(issues = issues + pkgRef.issues)
                }

                else -> null
            }
        }

    /**
     * Return a [Scope] created from the given type of [relation] for the [projectPackage][projectPackageId] in
     * [spdxDocument], or `null` if there are no such relations. Identified dependencies are mapped to ORT [Package]s
     * and then added to [packages].
     */
    private fun createScope(
        spdxDocument: SpdxResolvedDocument,
        projectPackageId: String,
        relation: SpdxRelationship.Type,
        packages: MutableSet<Package>,
        analyzerConfig: AnalyzerConfiguration
    ): Scope? =
        getDependencies(projectPackageId, spdxDocument, packages, mutableSetOf(), relation, analyzerConfig).takeUnless {
            it.isEmpty()
        }?.let {
            Scope(
                name = relation.name.removeSuffix("_DEPENDENCY_OF").lowercase(),
                dependencies = it
            )
        }

    override fun mapDefinitionFiles(
        analysisRoot: File,
        definitionFiles: List<File>,
        analyzerConfig: AnalyzerConfiguration
    ): List<File> =
        definitionFiles.associateWith {
            spdxDocumentCache.load(it).getOrNull()
        }.filter { (_, spdxDocument) ->
            // Distinguish whether we have a project-style SPDX document that describes a project and its dependencies,
            // or a package-style SPDX document that describes a single (dependency-)package.
            spdxDocument?.projectPackage() != null
        }.keys.also { remainingFiles ->
            if (remainingFiles.isEmpty()) return definitionFiles

            val discardedFiles = definitionFiles - remainingFiles
            if (discardedFiles.isNotEmpty()) {
                logger.info {
                    "Discarded the following ${discardedFiles.size} non-project SPDX files: " +
                        discardedFiles.joinToString { "'$it'" }
                }
            }
        }.toList()

    override fun resolveDependencies(
        analysisRoot: File,
        definitionFile: File,
        excludes: Excludes,
        includes: Includes,
        analyzerConfig: AnalyzerConfiguration,
        labels: Map<String, String>
    ): List<ProjectAnalyzerResult> {
        val transitiveDocument = SpdxResolvedDocument.load(spdxDocumentCache, definitionFile)

        val spdxDocument = transitiveDocument.rootDocument.document

        val packages = mutableSetOf<Package>()
        val scopes = mutableSetOf<Scope>()

        val projectPackage = requireNotNull(spdxDocument.projectPackage() ?: spdxDocument.packages.firstOrNull()) {
            "The SPDX document file at '$definitionFile' does not describe a project."
        }

        logger.info {
            "File '$definitionFile' contains SPDX document '${spdxDocument.name}' which describes project " +
                "'${projectPackage.name}'."
        }

        SPDX_SCOPE_RELATIONSHIPS.mapNotNullTo(scopes) { type ->
            createScope(transitiveDocument, projectPackage.spdxId, type, packages, analyzerConfig)
        }

        scopes += Scope(
            name = DEFAULT_SCOPE_NAME,
            dependencies = getDependencies(
                projectPackage.spdxId,
                transitiveDocument,
                packages,
                mutableSetOf(),
                analyzerConfig
            )
        )

        val purlReference = projectPackage.locateExternalReference(SpdxExternalReference.Type.Purl)
        val id = purlReference?.takeIf { config.deduceOrtIdFromPurl }?.run { toPackageUrl()?.toIdentifier() }
            ?: projectPackage.toIdentifier(projectType)

        val project = Project(
            id = id,
            cpe = projectPackage.locateCpe(),
            definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
            authors = projectPackage.originator.wrapPresentInSet(),
            declaredLicenses = setOf(projectPackage.licenseDeclared),
            vcs = processProjectVcs(definitionFile.parentFile, VcsInfo.EMPTY),
            homepageUrl = projectPackage.homepage.mapNotPresentToEmpty(),
            scopeDependencies = scopes
        )

        return listOf(ProjectAnalyzerResult(project, packages, transitiveDocument.getIssuesWithoutSpdxPackage()))
    }

    /**
     * Create the final [PackageManagerResult] by making sure that packages are removed from [projectResults] that
     * are also referenced as project dependencies.
     */
    override fun createPackageManagerResult(
        projectResults: Map<File, List<ProjectAnalyzerResult>>
    ): PackageManagerResult = PackageManagerResult(projectResults.filterProjectPackages())
}

internal fun getPackageManagerDependency(
    pkgId: String,
    doc: SpdxResolvedDocument,
    analyzerConfig: AnalyzerConfiguration
): PackageReference? {
    val issues = mutableListOf<Issue>()
    val spdxPackage = doc.getSpdxPackageForId(pkgId, issues) ?: return null
    val definitionFile = doc.getDefinitionFile(pkgId) ?: return null

    if (spdxPackage.packageFilename.isBlank()) return null

    val scope = spdxPackage.extractScopeFromExternalReferences() ?: return null

    val packageFile = definitionFile.resolveSibling(spdxPackage.packageFilename)

    if (packageFile.isFile) {
        val managedFiles = PackageManager.findManagedFiles(
            packageFile.parentFile,
            analyzerConfig.determineEnabledPackageManagers().map {
                val options = analyzerConfig.getPackageManagerConfiguration(it.descriptor.id)?.options.orEmpty()
                it.create(PluginConfig(options))
            }
        )

        managedFiles.forEach { (manager, files) ->
            if (files.any { it.canonicalPath == packageFile.canonicalPath }) {
                // TODO: The data from the spdxPackage is currently ignored, check if some fields need to be
                //       preserved somehow.
                return PackageManagerDependency(
                    packageManager = manager.descriptor.id,
                    definitionFile = VersionControlSystem.getPathInfo(packageFile).path,
                    scope = scope,
                    linkage = PackageLinkage.PROJECT_STATIC // TODO: Set linkage based on SPDX reference type.
                ).toPackageReference(issues)
            }
        }
    }

    return null
}

/**
 * Return the [PackageLinkage] between [dependency] and [dependant] as specified in [relationships]. If no
 * relationship is found, return [PackageLinkage.DYNAMIC].
 */
private fun getLinkageForDependency(
    dependency: SpdxPackage,
    dependant: String,
    relationships: List<SpdxRelationship>
): PackageLinkage =
    relationships.mapNotNull { relation ->
        SPDX_LINKAGE_RELATIONSHIPS[relation.relationshipType]?.takeIf {
            val relationId = if (relation.relatedSpdxElement.isExternalDocumentReferenceId()) {
                relation.relatedSpdxElement.substringAfter(":")
            } else {
                relation.relatedSpdxElement
            }

            relationId == dependency.spdxId && relation.spdxElementId == dependant
        }
    }.singleOrNull() ?: PackageLinkage.DYNAMIC

/**
 * Return true if the [relation] as defined in [relationships] describes an [SPDX_LINKAGE_RELATIONSHIPS] in the
 * [DEFAULT_SCOPE_NAME] so that the [source] depends on the [target].
 */
private fun hasDefaultScopeLinkage(
    source: String,
    target: String,
    relation: SpdxRelationship.Type,
    relationships: List<SpdxRelationship>
): Boolean {
    if (relation !in SPDX_LINKAGE_RELATIONSHIPS) return false

    val hasScopeRelationship = relationships.any {
        it.relationshipType in SPDX_SCOPE_RELATIONSHIPS
            // Scope relationships are defined in "reverse" as a "dependency of".
            && it.relatedSpdxElement == source && it.spdxElementId == target
    }

    return !hasScopeRelationship
}
