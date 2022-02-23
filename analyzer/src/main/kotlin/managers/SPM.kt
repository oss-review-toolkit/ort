/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.ProcessCapture
import org.ossreviewtoolkit.utils.ort.createOrtTempFile
import org.ossreviewtoolkit.utils.ort.normalizeVcsUrl

/**
 * The [Swift Package Manager](https://github.com/apple/swift-package-manager).
 */
class SPM(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analysisRoot, analyzerConfig, repoConfig), CommandLineTool {

    private val graphBuilder = DependencyGraphBuilder(SPMDependencyHandler())

    class Factory : AbstractPackageManagerFactory<SPM>("SPM") {
        override val globsForDefinitionFiles = listOf("Package.*", ".package.resolved")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = SPM(name, analysisRoot, analyzerConfig, repoConfig)
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

    /**
     * The output of the spm dependencies command.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class SpmDependenciesOutput(
        val identity: String,
        val name: String,
        val url: String,
        val version: String,
        val path: String,
        val dependencies: List<LibraryDependency>
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class LibraryDependency(
        @JsonProperty("name") val name: String,
        @JsonProperty("version") val version: String,
        @JsonProperty("url") val repositoryURL: String,
        @JsonProperty("dependencies") val dependencies: Set<LibraryDependency>
    ) : SPMDependency(repositoryURL) {

        override fun getVCSInfo(): VcsInfo {
            val vcsInfoFromUrl = VcsHost.parseUrl(repositoryURL)

            if (vcsInfoFromUrl.revision.isBlank()) {
                return vcsInfoFromUrl.copy(revision = version)
            }
            return vcsInfoFromUrl
        }

        override fun getIdentifier(): Identifier {
            val (author, project) = parseAuthorAndProjectFromRepo(repositoryURL)
            return Identifier(name, namespace = author.orEmpty(), version = version, name = project ?: name)
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
            val version: String? = null,
            val revision: String? = null,
            private val branch: String? = null
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
            val vcsInfoFromUrl = VcsHost.parseUrl(repositoryURL)
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
                type = "SPM",
                namespace = author.orEmpty(),
                name = project ?: packageName,
                version = state?.toString() ?: UNKNOWN,
            )
        }
    }

    override fun createPackageManagerResult(projectResults: Map<File, List<ProjectAnalyzerResult>>) =
        PackageManagerResult(projectResults, graphBuilder.build(), graphBuilder.packages())

    override fun mapDefinitionFiles(definitionFiles: List<File>): List<File> {
        return definitionFiles.filterNot { file -> file.path.contains(".build/checkouts") }
    }

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> {
        if (definitionFile.name == PACKAGE_SWIFT && !analyzerConfig.allowDynamicVersions) {
            throw IllegalArgumentException(
                "Resolving SPM dependencies from Package.swift might result in " +
                        "potentially unstable versions of dependencies. To support this, enable the " +
                        "'allowDynamicVersions' option in 'ort.conf'."
            )
        }

        if (definitionFile.name == PACKAGE_SWIFT) {
            return resolveLibraryDependencies(definitionFile)
        }

        return resolveAppDependencies(definitionFile)
    }

    /**
     * Resolves dependencies when the final build target is an app and no Package.swift is available.
     * This method parses dependencies from the Package.resolved file.
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
        val project = projectFromDefinitionFile(definitionFile)

        // When this command is run without building the project first, download information is printed to `stdout`.
        // To simplify parsing, write the output to a temporary file, which won't contain this download information.
        val resultFile = createOrtTempFile(managerName)

        ProcessCapture(
            command(),
            "package",
            "show-dependencies",
            "--format",
            "json",
            "--output-path",
            resultFile.absolutePath
        ).requireSuccess()

        val spmDependencies = jsonMapper.readValue(resultFile, SpmDependenciesOutput::class.java).dependencies

        val qualifiedScopeName = DependencyGraph.qualifyScope(scopeName = DEPENDENCIES_SCOPE, project = project)

        spmDependencies.onEach { graphBuilder.addDependency(qualifiedScopeName, it) }
            .map { libraryDependency -> libraryDependency.toPackage() }
            .toSortedSet()
            .also { graphBuilder.addPackages(it) }

        // TODO: I guess I need to add packages here?!
        // TODO: Issues might be thrown into stderr. Parse them and add it them to the result as well.
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

    override fun command(workingDir: File?) = if (Os.isWindows) "swift.exe" else "swift"

    override fun getVersion(workingDir: File?): String {
        val output = ProcessCapture(workingDir, command(), "package", getVersionArguments())

        return transformVersion(output.stdout)
    }

    override fun transformVersion(output: String) = output.removePrefix("Swift Package Manager - Swift ")

    companion object {
        private const val UNKNOWN = "Unknown"
        private const val PACKAGE_SWIFT = "Package.swift"
        private const val DEPENDENCIES_SCOPE = "dependencies"

        fun parseAuthorAndProjectFromRepo(repositoryURL: String): Pair<String?, String?> {
            val normalizedURL = normalizeVcsUrl(repositoryURL)
            val vcsHost = VcsHost.fromUrl(URI(normalizedURL))
            val project = vcsHost?.getProject(normalizedURL)
            val author = vcsHost?.getUserOrOrganization(normalizedURL)
            if (author.isNullOrBlank()) {
                logger.warn { "Unable to parse author name from $repositoryURL VCS URL. Results might be incomplete" }
            }
            if (project.isNullOrBlank()) {
                logger.warn { "Unable to parse project name from $repositoryURL VCS URL. Results might be incomplete" }
            }

            return author to project
        }
    }
}
