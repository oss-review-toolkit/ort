/*
 * Copyright (C) 2020 Bosch.IO GmbH
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
import java.util.SortedSet

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.spdx.SpdxConstants
import org.ossreviewtoolkit.spdx.SpdxModelMapper
import org.ossreviewtoolkit.spdx.model.SpdxDocument
import org.ossreviewtoolkit.spdx.model.SpdxPackage
import org.ossreviewtoolkit.spdx.model.SpdxRelationship
import org.ossreviewtoolkit.spdx.toSpdx

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
            line.removePrefix("Organization: ").takeIf { it != line }
        }.firstOrNull()

    private fun String.mapNotPresentToEmpty() = takeUnless { SpdxConstants.isNotPresent(it) }.orEmpty()

    /**
     * Return the dependencies of [pkg] defined in [doc] of the [SpdxRelationship.Type.DEPENDENCY_OF] type.
     */
    private fun getDependencies(pkg: SpdxPackage, doc: SpdxDocument): SortedSet<PackageReference> =
        getDependencies(pkg, doc, SpdxRelationship.Type.DEPENDENCY_OF) { target ->
            val dependency = doc.packages.single { it.spdxId == target }
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
            when {
                // Dependencies can either be defined on the target...
                target == pkg.spdxId && relation == dependencyOfRelation -> {
                    val dependency = doc.packages.single { it.spdxId == source }
                    PackageReference(
                        id = dependency.toIdentifier(),
                        dependencies = getDependencies(
                            dependency,
                            doc,
                            SpdxRelationship.Type.DEPENDENCY_OF,
                            dependsOnCase
                        )
                    )
                }

                // ...or on the source.
                source == pkg.spdxId && relation == SpdxRelationship.Type.DEPENDS_ON -> {
                    dependsOnCase(target)
                }

                else -> null
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

        val projectPackage = spdxDocument.packages.singleOrNull { it.packageFilename.isEmpty() }

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
                scopes = scopes
            )
        } else {
            // TODO: Add support for "package.spdx.yml" files. How to deal with relationships between SPDX packages if
            //       we do not have a project to attach dependencies to?
            Project.EMPTY
        }

        val nonProjectPackages = if (projectPackage != null) {
            spdxDocument.packages - projectPackage
        } else {
            spdxDocument.packages
        }

        val packages = nonProjectPackages.mapTo(sortedSetOf()) { pkg ->
            val packageDescription = pkg.description.takeUnless { it.isEmpty() } ?: pkg.summary
            val packageDir = workingDir.resolve(pkg.packageFilename)

            Package(
                id = pkg.toIdentifier(),
                declaredLicenses = sortedSetOf(pkg.licenseDeclared),
                concludedLicense = pkg.licenseConcluded.toSpdx(),
                description = packageDescription,
                homepageUrl = pkg.homepage.mapNotPresentToEmpty(),
                binaryArtifact = RemoteArtifact.EMPTY, // TODO: Use "downloadLocation" or "externalRefs"?
                sourceArtifact = RemoteArtifact.EMPTY, // TODO: Use "sourceInfo" or "externalRefs"?
                vcs = VersionControlSystem.forDirectory(packageDir)?.getInfo() ?: VcsInfo.EMPTY
            )
        }

        return listOf(ProjectAnalyzerResult(project, packages))
    }
}
