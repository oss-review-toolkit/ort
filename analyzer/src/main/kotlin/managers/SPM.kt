/*
 * Copyright (C) 2022 Bosch.IO GmbH
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

package org.ossreviewtoolkit.analyzer.managers

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper

import java.io.File
import java.net.URI

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerResult
import org.ossreviewtoolkit.analyzer.managers.utils.SPMDependencyHandler
import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.DependencyGraph
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.RemoteArtifact.Companion.EMPTY
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.utils.common.ProcessCapture
import org.ossreviewtoolkit.utils.core.ORT_CONFIG_FILENAME
import org.ossreviewtoolkit.utils.core.log
import org.ossreviewtoolkit.utils.core.normalizeVcsUrl

interface SPMCLIExecutor {
    fun executeSwift(definitionFile: File): JsonNode
}

/**
 * The [Swift Package Manager](https://github.com/apple/swift-package-manager).
 */
class SPM(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration,
    private val cliExecutor: SPMCLIExecutor
) : PackageManager(name, analysisRoot, analyzerConfig, repoConfig) {

    private val graphBuilder = DependencyGraphBuilder(SPMDependencyHandler())

    class Factory : AbstractPackageManagerFactory<SPM>(MANAGER_NAME), SPMCLIExecutor {
        override val globsForDefinitionFiles = listOf("Package.*", ".package.resolved")

        override fun executeSwift(definitionFile: File): JsonNode {
            val process = ProcessCapture(
                workingDir = definitionFile.parentFile,
                command = arrayOf("swift", "package", "show-dependencies", "--format", "json")
            ).requireSuccess()
            return ObjectMapper().readTree(process.stdout)
        }

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = SPM(managerName, analysisRoot, analyzerConfig, repoConfig, this)
    }

    abstract class SPMDependency(private val repositoryURL: String) {
        abstract fun getVCSInfo(): VcsInfo
        abstract fun getIdentifier(): Identifier

        fun toPackage(): Package {
            val (author, _) = parseAuthorAndProjectFromRepo(repositoryURL)
            return Package(
                vcs = getVCSInfo(),
                description = "",
                id = getIdentifier(),
                binaryArtifact = EMPTY,
                sourceArtifact = EMPTY,
                authors = sortedSetOf(author),
                declaredLicenses = sortedSetOf(), // SPM files don't provide License info. Leave this step for scanner.
                homepageUrl = repositoryURL.removeSuffix(".git")
            )
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class LibraryDependency(
        @JsonProperty("name") val name: String,
        @JsonProperty("version") val version: String,
        @JsonProperty("url") val repositoryURL: String,
        @JsonProperty("dependencies") val dependencies: Set<LibraryDependency>
    ) : SPMDependency(repositoryURL) {

        override fun getVCSInfo(): VcsInfo {
            val vcsInfoFromUrl = VcsHost.toVcsInfo(repositoryURL)

            if (vcsInfoFromUrl.revision.isBlank()) {
                return vcsInfoFromUrl.copy(revision = version)
            }
            return vcsInfoFromUrl
        }

        override fun getIdentifier(): Identifier {
            val (author, project) = parseAuthorAndProjectFromRepo(repositoryURL)
            return Identifier(MANAGER_NAME, namespace = author.orEmpty(), version = version, name = project ?: name)
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class AppDependency(
        @JsonProperty("package") val packageName: String,
        @JsonProperty("state") val state: AppDependencyState?,
        @JsonProperty("repositoryURL") val repositoryURL: String
    ) : SPMDependency(repositoryURL) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        data class AppDependencyState(
            @JsonProperty("version") val version: String? = null,
            @JsonProperty("revision") val revision: String? = null,
            @JsonProperty("branch") private val branch: String? = null
        ) {
            override fun toString(): String {
                return when {
                    !version.isNullOrBlank() -> version
                    !revision.isNullOrBlank() -> "revision-$revision"
                    !branch.isNullOrBlank() -> "branch-$branch"
                    else -> UNKNOWN
                }
            }
        }

        override fun getVCSInfo(): VcsInfo {
            val vcsInfoFromUrl = VcsHost.toVcsInfo(repositoryURL)
            if (vcsInfoFromUrl.revision.isBlank() && !state?.revision.isNullOrBlank()) {
                return vcsInfoFromUrl.copy(revision = state!!.revision!!)
            }
            if (vcsInfoFromUrl.revision.isBlank() && !state?.version.isNullOrBlank()) {
                return vcsInfoFromUrl.copy(revision = state!!.version!!)
            }
            return vcsInfoFromUrl
        }

        override fun getIdentifier(): Identifier {
            val (author, project) = parseAuthorAndProjectFromRepo(repositoryURL)
            return Identifier(
                type = MANAGER_NAME,
                namespace = author.orEmpty(),
                name = project ?: packageName,
                version = state?.toString() ?: UNKNOWN,
            )
        }
    }

    override fun createPackageManagerResult(projectResults: Map<File, List<ProjectAnalyzerResult>>) =
        PackageManagerResult(projectResults, graphBuilder.build(), graphBuilder.packages())

    override fun mapDefinitionFiles(definitionFiles: List<File>): List<File> {
        val validResolvedFiles = listOf("Package.resolved", ".package.resolved")
        val relevantDefinitionFiles = definitionFiles.filterNot { file -> file.path.contains(".build/checkouts") }

        val isPackageSwiftPresent = analysisRoot.resolve(PACKAGE_SWIFT).exists() || analysisRoot.name == PACKAGE_SWIFT

        if (!analyzerConfig.allowDynamicVersions && isPackageSwiftPresent) {
            log.info {
                "Skipping dependency resolution of $PACKAGE_SWIFT files because this potentially results in " +
                        "unstable versions of dependencies. To support this, enable the 'allowDynamicVersions' option" +
                        " in '$ORT_CONFIG_FILENAME'."
            }
            return relevantDefinitionFiles.filter { file -> validResolvedFiles.contains(file.name) }
        } else if (analyzerConfig.allowDynamicVersions && isPackageSwiftPresent) {
            return relevantDefinitionFiles.filter { file -> file.name == PACKAGE_SWIFT }
        }
        return relevantDefinitionFiles.filter { file -> validResolvedFiles.contains(file.name) }
    }

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> {
        if (definitionFile.name == PACKAGE_SWIFT) {
            return resolveLibraryDependencies(definitionFile)
        }
        return resolveAppDependencies(definitionFile)
    }

    /**
     * Resolves dependencies when the final build target is an app and no Package.swift is available.
     * This method parses dependencies from Package.resolved file.
     */
    private fun resolveAppDependencies(definitionFile: File): List<ProjectAnalyzerResult> {
        val mapper = ObjectMapper()
        val dependenciesJSON = mapper.readTree(definitionFile.readText()).get("object")?.get("pins")
        val appDependencies = dependenciesJSON?.map { json -> mapper.treeToValue(json, AppDependency::class.java) }

        return listOf(
            ProjectAnalyzerResult(
                project = projectFromDefinitionFile(definitionFile),
                packages = appDependencies?.mapNotNull { it.toPackage() }?.toSortedSet() ?: sortedSetOf()
            )
        )
    }

    /**
     * Resolves dependencies when the final build target is a library and Package.swift is available.
     * This method parses dependencies from `swift package show-dependencies --format json` output.
     * Also, this method provides parent-child associations for parsed dependencies.
     *
     * Only used when analyzerConfig.allowDynamicVersions is set to true.
     */
    private fun resolveLibraryDependencies(definitionFile: File): List<ProjectAnalyzerResult> {
        val mapper = ObjectMapper()
        val project = projectFromDefinitionFile(definitionFile)
        val dependenciesJSON = cliExecutor.executeSwift(definitionFile)
        val mappedDependencies = mapper.treeToValue(dependenciesJSON, LibraryDependency::class.java).dependencies

        val qualifiedScopeName = DependencyGraph.qualifyScope(scopeName = DEPENDENCIES_SCOPE, project = project)

        mappedDependencies.onEach { graphBuilder.addDependency(qualifiedScopeName, it) }
            .map { libraryDependency -> libraryDependency.toPackage() }
            .toSortedSet()
            .also { graphBuilder.addPackages(it) }

        return listOf(ProjectAnalyzerResult(project.copy(scopeNames = sortedSetOf(DEPENDENCIES_SCOPE)), sortedSetOf()))
    }

    private fun projectFromDefinitionFile(definitionFile: File): Project {
        val vcsInfo = VersionControlSystem.forDirectory(definitionFile.parentFile)?.getInfo().orEmpty()
        val (author, project) = parseAuthorAndProjectFromRepo(repositoryURL = vcsInfo.url)

        val projectIdentifier = Identifier(
            type = managerName,
            version = vcsInfo.revision,
            namespace = author.orEmpty(),
            name = project ?: definitionFile.parentFile.relativeTo(analysisRoot).invariantSeparatorsPath,
        )
        return Project(
            vcs = VcsInfo.EMPTY,
            id = projectIdentifier,
            authors = sortedSetOf(),
            declaredLicenses = sortedSetOf(),
            homepageUrl = normalizeVcsUrl(vcsInfo.url),
            vcsProcessed = processProjectVcs(definitionFile.parentFile),
            definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
        )
    }

    companion object {
        private const val UNKNOWN = "Unknown"
        private const val MANAGER_NAME = "SPM"
        private const val PACKAGE_SWIFT = "Package.swift"
        private const val DEPENDENCIES_SCOPE = "dependencies"

        fun parseAuthorAndProjectFromRepo(repositoryURL: String): Pair<String?, String?> {
            val normalizedURL = normalizeVcsUrl(repositoryURL)
            val vcsHost = VcsHost.toVcsHost(URI(normalizedURL))
            val project = vcsHost?.getProject(normalizedURL)
            val author = vcsHost?.getUserOrOrganization(normalizedURL)
            if (author.isNullOrBlank()) {
                log.warn { "Unable to parse author name from $repositoryURL VCS URL. Results might be incomplete" }
            }
            if (project.isNullOrBlank()) {
                log.warn { "Unable to parse project name from $repositoryURL VCS URL. Results might be incomplete" }
            }

            return author to project
        }
    }
}
