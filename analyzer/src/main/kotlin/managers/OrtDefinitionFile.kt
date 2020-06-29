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

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.readValue

import java.io.File
import java.io.IOException
import java.util.SortedSet

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.mapper

/**
 * This is a "fake" package manager which uses a definition file format that closely resembles ORT's result file format
 * in order to just take as given what is defined in these files. The main use case for this package manager is to
 * capture dependencies that otherwise would not be automatically found, like source code of packages committed to sub-
 * directories (or referenced via Git submodules) in the project repository, which is a typical approach in C/C++
 * projects without a dedicated package manager.
 */
class OrtDefinitionFile(
    managerName: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(managerName, analysisRoot, analyzerConfig, repoConfig) {
    class Factory : AbstractPackageManagerFactory<OrtDefinitionFile>("OrtDefinitionFile") {
        override val globsForDefinitionFiles = listOf("ortDefinitionFile.yml")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = OrtDefinitionFile(managerName, analysisRoot, analyzerConfig, repoConfig)
    }

    private data class DefinitionFile(
        val projects: List<DefinedProject>
    )

    private data class DefinedProject(
        val id: Identifier,
        val declaredLicenses: SortedSet<String> = sortedSetOf(),
        val homepageUrl: String = "",
        val scopes: SortedSet<Scope> = sortedSetOf()
    )

    /**
     * A custom deserializer for the Identifier class that allows to only specify two components that describe the
     * namespace and name, or three components that describe the namespace, name and version for a project.
     */
    private inner class IdentifierDeserializer : StdDeserializer<Identifier>(Identifier::class.java) {
        override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Identifier {
            val node = p.codec.readTree<JsonNode>(p)
            val id = node.textValue()
            val components = id.split(':')
            return when (components.size) {
                2 -> Identifier(managerName, components[0], components[1], "")
                3 -> Identifier(managerName, components[0], components[1], components[2])
                else -> throw IOException("Identifier '$id' must consist of 2 or 3 components separated by ':'.")
            }
        }
    }

    override fun resolveDependencies(definitionFile: File): List<ProjectAnalyzerResult> {
        val results = mutableListOf<ProjectAnalyzerResult>()

        val workingDir = definitionFile.parentFile
        val definitions = definitionFile.mapper().copy()
            .registerModule(SimpleModule().addDeserializer(Identifier::class.java, IdentifierDeserializer()))
            .readValue<DefinitionFile>(definitionFile)

        definitions.projects.forEach { definedProject ->
            val project = Project(
                id = definedProject.id,
                definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
                declaredLicenses = definedProject.declaredLicenses,
                vcs = VcsInfo.EMPTY,
                vcsProcessed = processProjectVcs(workingDir),
                homepageUrl = definedProject.homepageUrl,
                scopes = definedProject.scopes
            )

            val packages = definedProject.scopes.flatMapTo(sortedSetOf()) { scope ->
                scope.dependencies.map { pkgRef ->
                    // A dependency package's name is interpreted as the path for the package relative to the directory
                    // the definition file is located in.
                    val packageDir = workingDir.resolve(pkgRef.id.name)

                    require(packageDir.isDirectory) {
                        val path = packageDir.absolutePath
                        "For package '${pkgRef.id.toCoordinates()}', the directory '$path' does not exist."
                    }

                    Package(
                        id = pkgRef.id,
                        declaredLicenses = sortedSetOf(),
                        description = "",
                        homepageUrl = "",
                        binaryArtifact = RemoteArtifact.EMPTY,
                        sourceArtifact = RemoteArtifact.EMPTY,
                        vcs = VersionControlSystem.forDirectory(packageDir)?.getInfo() ?: VcsInfo.EMPTY
                    ).toCuratedPackage()
                }
            }

            results += ProjectAnalyzerResult(project, packages)
        }

        return results
    }
}
