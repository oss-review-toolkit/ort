/*
 * Copyright (C) 2020-2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.analyzer.managers

import java.io.File
import java.io.FileNotFoundException
import java.net.URI
import java.net.URISyntaxException
import java.util.SortedSet

import kotlin.io.path.createTempFile

import okhttp3.Request

import okio.buffer
import okio.sink

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.downloader.DownloadException
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.spdx.SpdxConstants
import org.ossreviewtoolkit.spdx.SpdxExpression
import org.ossreviewtoolkit.spdx.SpdxModelMapper
import org.ossreviewtoolkit.spdx.model.SpdxDocument
import org.ossreviewtoolkit.spdx.model.SpdxExternalDocumentReference
import org.ossreviewtoolkit.spdx.model.SpdxPackage
import org.ossreviewtoolkit.spdx.model.SpdxRelationship
import org.ossreviewtoolkit.spdx.toSpdx
import org.ossreviewtoolkit.utils.ORT_NAME
import org.ossreviewtoolkit.utils.OkHttpClientHelper
import org.ossreviewtoolkit.utils.isValidUri

/**
 * Determines if an [SpdxDocument] is a project.spdx or
 * a package.spdx document. If there is only one package
 * in the packages list or there is no package that does
 * not have a [SpdxPackage.packageFilename]the document
 * is regarded as a package.spdx document.
 */
private fun SpdxDocument.isProject(): Boolean {
    return if (projectPackage() != null) {
        val spdxPackage = packages.singleOrNull()
        spdxPackage == null
    } else {
        false
    }
}

/**
 * Returns a single package that has no [SpdxPackage.packageFilename]
 * if there is only one, marking it as the project.spdx document.
 */
private fun SpdxDocument.projectPackage(): SpdxPackage? {
    return packages.singleOrNull { it.packageFilename.isEmpty() }
}

private const val DEFAULT_SCOPE_NAME = "default"

/**
 * A "fake" package manager implementation that uses SPDX documents as definition files to declare projects and describe
 * packages. See https://github.com/spdx/spdx-spec/issues/439 for details.
 */
class SpdxDocumentFile(
    managerName: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(managerName, analysisRoot, analyzerConfig, repoConfig) {
    var packagesFromExternalDocumentReferences = mutableMapOf<String, SpdxPackage>()

    class Factory : AbstractPackageManagerFactory<SpdxDocumentFile>("SpdxDocumentFile") {
        override val globsForDefinitionFiles = listOf("*.spdx.yml", "*.spdx.yaml", "*.spdx.json")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = SpdxDocumentFile(managerName, analysisRoot, analyzerConfig, repoConfig)
    }

    private fun SpdxPackage.toIdentifier() =
        Identifier(
            type = managerName,
            namespace = originator.extractOrganization().orEmpty(),
            name = name,
            version = versionInfo
        )

    private fun String.extractOrganization() =
        lineSequence().mapNotNull { line ->
            line.removePrefix(SpdxConstants.ORGANIZATION).takeIf { it != line }
        }.firstOrNull()

    private fun String.mapNotPresentToEmpty() = takeUnless { SpdxConstants.isNotPresent(it) }.orEmpty()

    /**
     * Return the dependencies of [pkg] defined in [doc] of the [SpdxRelationship.Type.DEPENDENCY_OF] type.
     */
    private fun getDependencies(pkg: SpdxPackage, doc: SpdxDocument): SortedSet<PackageReference> =
        getDependencies(pkg, doc, SpdxRelationship.Type.DEPENDENCY_OF) { target ->
            val dependency = getSpdxPackageFromId(doc, target)
            PackageReference(
                id = dependency.toIdentifier(),
                dependencies = getDependencies(dependency, doc)
            )
        }

    /**
     * Return the dependencies of [pkg] defined in [doc] of the given [dependencyOfRelation] type. Optionally, the
     * [SpdxRelationship.Type.DEPENDS_ON] type is handled by [dependsOnCase].
     */
    private fun getDependencies(
        pkg: SpdxPackage,
        doc: SpdxDocument,
        dependencyOfRelation: SpdxRelationship.Type,
        dependsOnCase: (String) -> PackageReference? = { null }
    ): SortedSet<PackageReference> =
        doc.relationships.mapNotNullTo(sortedSetOf()) { (source, target, relation) ->
            val issues = mutableListOf<OrtIssue>()

            when {
                // Dependencies can either be defined on the target...
                pkg.spdxId.equals(target, ignoreCase = true) && relation == dependencyOfRelation -> {
                    if (pkg.spdxId != target) {
                        issues += createAndLogIssue(
                            source = managerName,
                            message = "Source '${pkg.spdxId}' has to match target '$target' case-sensitively."
                        )
                    }

                    val dependency = getSpdxPackageFromId(doc, source)
                    PackageReference(
                        id = dependency.toIdentifier(),
                        dependencies = getDependencies(
                            dependency,
                            doc,
                            SpdxRelationship.Type.DEPENDENCY_OF,
                            dependsOnCase
                        ),
                        issues = issues
                    )
                }

                // ...or on the source.
                pkg.spdxId.equals(source, ignoreCase = true) && relation == SpdxRelationship.Type.DEPENDS_ON -> {
                    if (pkg.spdxId != source) {
                        issues += createAndLogIssue(
                            source = managerName,
                            message = "Source '$source' has to match target '${pkg.spdxId}' case-sensitively."
                        )
                    }

                    val pkgRef = dependsOnCase(target)
                    pkgRef?.copy(issues = issues + pkgRef.issues)
                }

                else -> null
            }
        }

    /**
     * Maps a given identifier on an spdx package or external document reference
     * @param doc spdx document containing all packages or external document references the id could refer to
     * @param source the identifier to be found in the spdx document
     */
    private fun getSpdxPackageFromId(
        doc: SpdxDocument,
        source: String
    ): SpdxPackage {
        val fromPackage = doc.packages.singleOrNull { it.spdxId == source }
        if (fromPackage != null) {
            return fromPackage
        }
        val externalDocumentReference = doc.externalDocumentRefs.singleOrNull {
            it.externalDocumentId == source.split(":").first()
        }
            ?: throw IllegalArgumentException("No single package or externalDocumentRef with " +
                    "source ID '$source' found.")

        val externalDocumentReferenceToSpdxPackage =
            packagesFromExternalDocumentReferences[externalDocumentReference.externalDocumentId]
                ?: externalDocumentReferenceToSpdxPackage(externalDocumentReference)

        packagesFromExternalDocumentReferences[externalDocumentReference.externalDocumentId] =
            externalDocumentReferenceToSpdxPackage
        return externalDocumentReferenceToSpdxPackage
    }

    /**
     * Maps [SpdxExternalDocumentReference.spdxDocument] to a package
     * @param externalDocumentReference external document reference containing a spdx document with a uri to a spdx
     * document that can be mapped to a [SpdxPackage]
     * @throws IllegalArgumentException
     * @returns spdx package the spdx document uri of the externalDocumentReference was referring to
     */
    private fun externalDocumentReferenceToSpdxPackage(externalDocumentReference: SpdxExternalDocumentReference):
            SpdxPackage {
        val externalSpdxDocument = getFileFromExternalDocumentReference(externalDocumentReference)

        if (listOf("json", "yaml", "yml").contains(externalSpdxDocument.extension)) {
            val spdxDocument = SpdxModelMapper.read(externalSpdxDocument, SpdxDocument::class.java)
            if (!spdxDocument.isProject()) {
                return spdxDocument.packages.single()
            } else {
                throw IllegalArgumentException("${externalDocumentReference.externalDocumentId} refers to a file " +
                        "that contains more than a single package. This is currently not supported yet.")
            }
        } else {
            throw IllegalArgumentException("${externalDocumentReference.externalDocumentId} refers to a file that " +
                    "does not have a supported file extension ")
        }
    }

    /**
     * Returns [File] referred to by [SpdxExternalDocumentReference.spdxDocument]. Can be either locally or remotely
     * fetched.
     */
    private fun getFileFromExternalDocumentReference(externalDocumentReference: SpdxExternalDocumentReference): File {
        val spdxDocument = externalDocumentReference.spdxDocument
        if (spdxDocument.isValidUri()) {
            try {
                val uri = URI(spdxDocument)
                if (uri.scheme.equals("file", ignoreCase = true) || !uri.isAbsolute) {
                    return getFileOrFail(uri.path)
                }
                return requestSpdxDocument(uri)
            } catch (e: URISyntaxException) {
                return getFileOrFail(spdxDocument)
            }
        } else {
            throw IllegalArgumentException("Uri $spdxDocument supplied by " +
                    "${externalDocumentReference.externalDocumentId} is not valid. }")
        }
    }

    /**
     * Returns [File] from path
     */
    private fun getFileOrFail(path: String): File {
        val file = File(path)
        return if (file.exists()) {
            file
        } else {
            throw FileNotFoundException("The file $path does not exist. ")
        }
    }

    /**
     * Returns [File] from a given [URI] if it can be found.
     * @throws DownloadException when file can not be downloaded.
     */
    private fun requestSpdxDocument(uri: URI): File {
        val request = Request.Builder()
            .get()
            .url(uri.toURL())
            .build()

        OkHttpClientHelper.execute(request).use { response ->
            val body = response.body

            if (!response.isSuccessful || body == null) {
                throw DownloadException(
                    "Failed to download spdx document $uri: $response"
                )
            }

            val fileName = response.request.url.pathSegments.last()

            return createTempFile(ORT_NAME, fileName).toFile().also { tempFile ->
                tempFile.sink().buffer().use { it.writeAll(body.source()) }
                tempFile.deleteOnExit()
            }
        }
    }

    /**
     * Return a [Scope] created from the given type of [relation] for [projectPackage] in [spdxDocument], or `null` if
     * there are no such relations.
     */
    private fun createScope(
        spdxDocument: SpdxDocument,
        projectPackage: SpdxPackage,
        relation: SpdxRelationship.Type
    ): Scope? =
        getDependencies(projectPackage, spdxDocument, relation).takeUnless { it.isEmpty() }?.let { dependencies ->
            Scope(
                name = relation.name.removeSuffix("_DEPENDENCY_OF").toLowerCase(),
                dependencies = dependencies
            )
        }

    override fun resolveDependencies(definitionFile: File): List<ProjectAnalyzerResult> {
        val workingDir = definitionFile.parentFile
        val spdxDocument = SpdxModelMapper.read(definitionFile, SpdxDocument::class.java)

        // Distinguish whether we have a project-style SPDX document that describes a project and its dependencies, or a
        // package-style SPDX document that describes a single (dependency-)package.
        val projectPackage = spdxDocument.projectPackage()

        val project = if (projectPackage != null) {
            val scopes = listOfNotNull(
                Scope(
                    name = DEFAULT_SCOPE_NAME,
                    dependencies = getDependencies(projectPackage, spdxDocument)
                ),
                createScope(spdxDocument, projectPackage, SpdxRelationship.Type.BUILD_DEPENDENCY_OF),
                createScope(spdxDocument, projectPackage, SpdxRelationship.Type.DEV_DEPENDENCY_OF),
                createScope(spdxDocument, projectPackage, SpdxRelationship.Type.OPTIONAL_DEPENDENCY_OF),
                createScope(spdxDocument, projectPackage, SpdxRelationship.Type.PROVIDED_DEPENDENCY_OF),
                createScope(spdxDocument, projectPackage, SpdxRelationship.Type.RUNTIME_DEPENDENCY_OF),
                createScope(spdxDocument, projectPackage, SpdxRelationship.Type.TEST_DEPENDENCY_OF)
            ).toSortedSet()

            Project(
                id = projectPackage.toIdentifier(),
                definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
                declaredLicenses = sortedSetOf(projectPackage.licenseDeclared),
                vcs = VcsInfo.EMPTY,
                vcsProcessed = processProjectVcs(workingDir, VcsInfo.EMPTY, projectPackage.homepage),
                homepageUrl = projectPackage.homepage.mapNotPresentToEmpty(),
                scopeDependencies = scopes
            )
        } else {
            // TODO: Add support for "package.spdx.yml" files. How to deal with relationships between SPDX packages if
            //       we do not have a project to attach dependencies to?
            Project.EMPTY.copy(id = Identifier.EMPTY.copy(type = managerName))
        }

        val nonProjectPackages = if (projectPackage != null) {
            spdxDocument.packages - projectPackage + packagesFromExternalDocumentReferences.values
        } else {
            spdxDocument.packages + packagesFromExternalDocumentReferences.values
        }

        val packages = nonProjectPackages.mapTo(sortedSetOf()) { pkg ->
            val packageDescription = pkg.description.takeUnless { it.isEmpty() } ?: pkg.summary
            val packageDir = workingDir.resolve(pkg.packageFilename)

            Package(
                id = pkg.toIdentifier(),
                declaredLicenses = sortedSetOf(pkg.licenseDeclared),
                concludedLicense = getConcludedLicense(pkg),
                description = packageDescription,
                homepageUrl = pkg.homepage.mapNotPresentToEmpty(),
                binaryArtifact = RemoteArtifact.EMPTY, // TODO: Use "downloadLocation" or "externalRefs"?
                sourceArtifact = getSourceArtifact(pkg),
                vcs = VersionControlSystem.forDirectory(packageDir)?.getInfo() ?: VcsInfo.EMPTY
            )
        }

        return listOf(ProjectAnalyzerResult(project, packages))
    }

    /**
     * Return the concluded license to be used in ORT's data model, which expects a not present value to be null instead
     * of NONE or NOASSERTION.
     */
    private fun getConcludedLicense(pkg: SpdxPackage): SpdxExpression? =
        pkg.licenseConcluded.takeIf { SpdxConstants.isPresent(it) }?.toSpdx()

    /**
     * Return a [RemoteArtifact] created from the downloadLocation of the given package [SpdxPackage] if it is a local
     * file, or return an [RemoteArtifact.EMPTY].
     *
     * TODO: Consider also taking "sourceInfo" or "externalRefs" into account.
     */
    private fun getSourceArtifact(pkg: SpdxPackage): RemoteArtifact {
        return if (pkg.downloadLocation.startsWith("file:/")) {
            RemoteArtifact(pkg.downloadLocation, Hash.NONE)
        } else {
            RemoteArtifact.EMPTY
        }
    }
}
