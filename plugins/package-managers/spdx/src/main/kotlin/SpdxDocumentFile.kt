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

@file:Suppress("TooManyFunctions")

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
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginConfig
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.packagemanagers.spdx.utils.SpdxDocumentCache
import org.ossreviewtoolkit.plugins.packagemanagers.spdx.utils.SpdxResolvedDocument
import org.ossreviewtoolkit.utils.common.collapseWhitespace
import org.ossreviewtoolkit.utils.common.getQueryParameters
import org.ossreviewtoolkit.utils.common.toUri
import org.ossreviewtoolkit.utils.common.withoutPrefix
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.toSpdx
import org.ossreviewtoolkit.utils.spdxdocument.model.SpdxDocument
import org.ossreviewtoolkit.utils.spdxdocument.model.SpdxExternalDocumentReference
import org.ossreviewtoolkit.utils.spdxdocument.model.SpdxExternalReference
import org.ossreviewtoolkit.utils.spdxdocument.model.SpdxPackage
import org.ossreviewtoolkit.utils.spdxdocument.model.SpdxRelationship

private const val DEFAULT_SCOPE_NAME = "default"

private val SPDX_LINKAGE_RELATIONSHIPS = mapOf(
    SpdxRelationship.Type.DYNAMIC_LINK to PackageLinkage.DYNAMIC,
    SpdxRelationship.Type.STATIC_LINK to PackageLinkage.STATIC
)

private val SPDX_SCOPE_RELATIONSHIPS = SpdxRelationship.Type.entries.filter { it.name.endsWith("_DEPENDENCY_OF") }

private val SPDX_VCS_PREFIXES = mapOf(
    "git+" to VcsType.GIT,
    "hg+" to VcsType.MERCURIAL,
    "bzr+" to VcsType.UNKNOWN,
    "svn+" to VcsType.SUBVERSION
)

/**
 * Return true if the [SpdxDocument] describes a project. Otherwise, if it describes a package, return false.
 */
private fun SpdxDocument.isProject(): Boolean = projectPackage() != null

/**
 * Return the [SpdxPackage] in the [SpdxDocument] that denotes a project, or null if no project but only packages are
 * defined.
 */
internal fun SpdxDocument.projectPackage(): SpdxPackage? =
    // An SpdxDocument that describes a project must have at least 2 packages, one for the project itself, and another
    // one for at least one dependency package.
    packages.takeIf { it.size > 1 || (it.size == 1 && externalDocumentRefs.isNotEmpty()) }
        // The package that describes a project must have an "empty" package filename (as the "filename" is the project
        // directory itself).
        ?.singleOrNull { it.packageFilename.isEmpty() || it.packageFilename == "." }

/**
 * Try to find an [SpdxExternalReference] in this [SpdxPackage] of type purl from which the scope of a
 * package manager dependency can be extracted. Return this scope or *null* if cannot be determined.
 */
internal fun SpdxPackage.extractScopeFromExternalReferences(): String? =
    externalRefs.filter { it.referenceType == SpdxExternalReference.Type.Purl }
        // Need to convert the URI to a hierarchical one; otherwise query parameters cannot be extracted.
        .map { it.referenceLocator.replace("pkg:", "pkg://") }
        .firstNotNullOfOrNull {
            it.toUri { uri -> uri.getQueryParameters()["scope"]?.singleOrNull() }.getOrNull()
        }

/**
 * Return the declared license to be used in ORT's data model, which expects a not present value to be an empty set
 * instead of NONE or NOASSERTION.
 */
private fun SpdxPackage.getDeclaredLicense(): Set<String> =
    setOfNotNull(licenseDeclared.takeIf { SpdxConstants.isPresent(it) })

/**
 * Return the concluded license to be used in ORT's data model, which uses null instead of NOASSERTION.
 */
private fun SpdxPackage.getConcludedLicense(): SpdxExpression? =
    licenseConcluded.takeUnless { it == SpdxConstants.NOASSERTION }?.toSpdx()

/**
 * Return a [RemoteArtifact] for the artifact that the [downloadLocation][SpdxPackage.downloadLocation] points to. If
 * the download location is a "not present" value, or if it points to a VCS location instead, return null.
 */
private fun SpdxPackage.getRemoteArtifact(): RemoteArtifact? =
    when {
        SpdxConstants.isNotPresent(downloadLocation) -> null
        SPDX_VCS_PREFIXES.any { (prefix, _) -> downloadLocation.startsWith(prefix) } -> null
        else -> {
            if (downloadLocation.endsWith(".git")) {
                logger.warn {
                    "The download location $downloadLocation of SPDX package '$spdxId' looks like a Git repository " +
                        "URL but it lacks the 'git+' prefix and thus will be treated as an artifact URL."
                }
            }

            RemoteArtifact(downloadLocation, Hash.NONE)
        }
    }

/**
 * Return the [VcsInfo] contained in the [downloadLocation][SpdxPackage.downloadLocation], or null if the download
 * location is a "not present" value / does not point to a VCS location.
 */
internal fun SpdxPackage.getVcsInfo(): VcsInfo? {
    if (SpdxConstants.isNotPresent(downloadLocation)) return null

    return SPDX_VCS_PREFIXES.mapNotNull { (prefix, vcsType) ->
        downloadLocation.withoutPrefix(prefix)?.let { url ->
            var vcsUrl = url

            val vcsPath = vcsUrl.substringAfterLast('#', "")
            vcsUrl = vcsUrl.removeSuffix("#$vcsPath")

            val vcsRevision = vcsUrl.substringAfterLast('@', "")
            vcsUrl = vcsUrl.removeSuffix("@$vcsRevision")

            VcsInfo(vcsType, vcsUrl, vcsRevision, path = vcsPath)
        }
    }.firstOrNull()
}

/**
 * Return the location of the first [external reference][SpdxExternalReference] of the given [type] in this
 * [SpdxPackage], or null if there is no such reference.
 */
private fun SpdxPackage.locateExternalReference(type: SpdxExternalReference.Type): String? =
    externalRefs.find { it.referenceType == type }?.referenceLocator

/**
 * Return a CPE identifier for this package if present. Search for all CPE versions.
 */
private fun SpdxPackage.locateCpe(): String? =
    locateExternalReference(SpdxExternalReference.Type.Cpe23Type)
        ?: locateExternalReference(SpdxExternalReference.Type.Cpe22Type)

/**
 * Return whether the string has the format of an [SpdxExternalDocumentReference], with or without an additional
 * package id.
 */
private fun String.isExternalDocumentReferenceId(): Boolean = startsWith(SpdxConstants.DOCUMENT_REF_PREFIX)

/**
 * Map a "not preset" SPDX value, i.e. NONE or NOASSERTION, to an empty string.
 */
private fun String.mapNotPresentToEmpty(): String = takeUnless { SpdxConstants.isNotPresent(it) }.orEmpty()

/**
 * Sanitize a string for use as an [Identifier] property where colons are not supported by replacing them with spaces,
 * trimming, and finally collapsing multiple consecutive spaces.
 */
private fun String.sanitize(): String = replace(':', ' ').collapseWhitespace()

/**
 * Wrap any "present" SPDX value in a sorted set, or return an empty sorted set otherwise.
 */
private fun String?.wrapPresentInSet(): Set<String> {
    if (SpdxConstants.isPresent(this)) {
        withoutPrefix(SpdxConstants.PERSON)?.let { persons ->
            // In case of a person, allow a comma-separated list of persons.
            return persons.split(',').mapTo(mutableSetOf()) { it.trim() }
        }

        // Do not split an organization like "Acme, Inc." by comma.
        withoutPrefix(SpdxConstants.ORGANIZATION)?.let {
            return setOf(it.trim())
        }
    }

    return emptySet()
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

/**
 * A "fake" package manager implementation that uses SPDX documents as definition files to declare projects and describe
 * packages. See https://github.com/spdx/spdx-spec/issues/439 for details.
 */
@OrtPlugin(
    displayName = "SpdxDocumentFile",
    description = "A package manager that uses SPDX documents as definition files.",
    factory = PackageManagerFactory::class
)
class SpdxDocumentFile(override val descriptor: PluginDescriptor = SpdxDocumentFileFactory.descriptor) :
    PackageManager("SpdxDocumentFile") {
    override val globsForDefinitionFiles = listOf("*.spdx.yml", "*.spdx.yaml", "*.spdx.json")

    private val spdxDocumentCache = SpdxDocumentCache()

    /**
     * Create an [Identifier] out of this [SpdxPackage].
     */
    private fun SpdxPackage.toIdentifier() =
        Identifier(
            type = projectType,
            namespace = listOfNotNull(supplier, originator).firstOrNull()
                ?.withoutPrefix(SpdxConstants.ORGANIZATION).orEmpty().sanitize(),
            name = name.sanitize(),
            version = versionInfo.sanitize()
        )

    /**
     * Create a [Package] out of this [SpdxPackage].
     */
    private fun SpdxPackage.toPackage(definitionFile: File?, doc: SpdxResolvedDocument): Package {
        val packageDescription = description.ifEmpty { summary }

        // If the VCS information cannot be determined from the VCS working tree itself, fall back to try getting it
        // from the download location.
        val packageDir = definitionFile?.resolveSibling(packageFilename)
        val vcs = packageDir?.let { VersionControlSystem.forDirectory(it)?.getInfo() } ?: getVcsInfo().orEmpty()

        val generatedFromRelations = doc.relationships.filter {
            it.relationshipType == SpdxRelationship.Type.GENERATED_FROM
        }

        val isBinaryArtifact = generatedFromRelations.any { it.spdxElementId == spdxId }
            && generatedFromRelations.none { it.relatedSpdxElement == spdxId }

        val id = toIdentifier()
        val artifact = getRemoteArtifact()

        val purl = locateExternalReference(SpdxExternalReference.Type.Purl) ?: buildString {
            append(id.toPurl())

            val qualifiers = listOfNotNull(
                artifact?.url?.let { "download_url=$it" },
                artifact?.hash?.takeIf { it.algorithm in HashAlgorithm.VERIFIABLE }
                    ?.let { "checksum=${it.algorithm.name.lowercase()}:${it.value}" }
            )

            if (qualifiers.isNotEmpty()) append(qualifiers.joinToString(separator = "&", prefix = "?"))
        }

        return Package(
            id = id,
            purl = purl,
            cpe = locateCpe(),
            authors = originator.wrapPresentInSet(),
            declaredLicenses = getDeclaredLicense(),
            concludedLicense = getConcludedLicense(),
            description = packageDescription,
            homepageUrl = homepage.mapNotPresentToEmpty(),
            binaryArtifact = artifact.takeIf { isBinaryArtifact }.orEmpty(),
            sourceArtifact = artifact.takeUnless { isBinaryArtifact }.orEmpty(),
            vcs = vcs
        )
    }

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
                    packages += dependency.toPackage(doc.getDefinitionFile(target), doc)

                    PackageReference(
                        id = dependency.toIdentifier(),
                        dependencies = getDependencies(target, doc, packages, ancestorIds, analyzerConfig),
                        linkage = getLinkageForDependency(dependency, pkgId, doc.relationships),
                        issues = issues
                    )
                }
        }.also { ancestorIds.remove(pkgId) }
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
            val managedFiles = findManagedFiles(
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
                            packages += dependency.toPackage(doc.getDefinitionFile(source), doc)
                            PackageReference(
                                id = dependency.toIdentifier(),
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
            spdxDocument?.isProject() == true
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
        analyzerConfig: AnalyzerConfiguration,
        labels: Map<String, String>
    ): List<ProjectAnalyzerResult> {
        val transitiveDocument = SpdxResolvedDocument.load(spdxDocumentCache, definitionFile)

        val spdxDocument = transitiveDocument.rootDocument.document

        val packages = mutableSetOf<Package>()
        val scopes = mutableSetOf<Scope>()

        val projectPackage = if (!spdxDocument.isProject()) {
            spdxDocument.packages.first()
        } else {
            requireNotNull(spdxDocument.projectPackage()) {
                "The SPDX document file at '$definitionFile' does not describe a project."
            }
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

        val project = Project(
            id = projectPackage.toIdentifier(),
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
