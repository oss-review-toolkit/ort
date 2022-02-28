/*
 * Copyright (C) 2020 Bosch.IO GmbH
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
import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.RemoteArtifact.Companion.EMPTY
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.utils.common.ProcessCapture
import org.ossreviewtoolkit.utils.core.ORT_CONFIG_FILENAME
import org.ossreviewtoolkit.utils.core.log
import org.ossreviewtoolkit.utils.core.normalizeVcsUrl

/**
 * The [Swift Package manager](https://github.com/apple/swift-package-manager) package manager for Swift.
 */
class SPM(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analysisRoot, analyzerConfig, repoConfig) {

    private companion object {
        const val UNKNOWN = "Unknown"
        const val PACKAGE_SWIFT = "Package.swift"
        const val PACKAGE_RESOLVED = "Package.resolved"
    }

    class Factory : AbstractPackageManagerFactory<SPM>("SPM") {
        override val globsForDefinitionFiles = listOf(PACKAGE_RESOLVED, ".package.resolved")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = SPM(managerName, analysisRoot, analyzerConfig, repoConfig)
    }

    /**
     * Custom mapping for definition files is needed to combat differences that Swift Package Manager implies based on
     * the build target. If the build target is an application, the Package.resolved files would exist, but if a build
     * target is a library, only Package.swift files would exist. We would use `swift package show-dependencies`
     * to convert a Package.swift file to a Package.resolved file, that can later be parsed. Also, when the build target
     * is a library, the .build/checkouts directory would exist with internal Package.resolved files. These internal
     * Package.resolved are unnecessary for us to parse since the top-level Package.resolved will contain the full
     * dependency graph, with transitive dependencies included. So to speed up the analyzer, these files are excluded.
     * See https://www.swift.org/package-manager/ for reference
     */

    override fun mapDefinitionFiles(definitionFiles: List<File>): List<File> {

        fun filterInternalPackageResolvedFiles(): List<File> {
            return definitionFiles.filterNot { file -> file.path.contains(".build/checkouts") }
        }

        if (analysisRoot.resolve(PACKAGE_RESOLVED).exists() || !analysisRoot.resolve(PACKAGE_SWIFT).exists()) {
            return filterInternalPackageResolvedFiles()
        }

        if (!analyzerConfig.allowDynamicVersions) {
            log.info {
                "Skipping dependency resolution of $PACKAGE_SWIFT files because this potentially results in " +
                        "unstable versions of dependencies. To support this, enable the 'allowDynamicVersions' option" +
                        " in '$ORT_CONFIG_FILENAME'."
            }
            return filterInternalPackageResolvedFiles()
        }

        ProcessCapture(workingDir = analysisRoot, command = arrayOf("swift", "package", "show-dependencies"))
        val packageResolvedFile = File(analysisRoot, PACKAGE_RESOLVED)
        if (!packageResolvedFile.exists()) {
            return filterInternalPackageResolvedFiles()
        }
        return listOf(packageResolvedFile) + filterInternalPackageResolvedFiles()
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class SPMDependency(
        @JsonProperty("package") val packageName: String,
        @JsonProperty("state") val state: SPMDependencyState?,
        @JsonProperty("repositoryURL") val repositoryURL: String
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        data class SPMDependencyState(
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

        fun toVCSInfo(): VcsInfo {
            val vcsInfoFromUrl = VcsHost.toVcsInfo(repositoryURL)
            if (vcsInfoFromUrl.revision.isBlank() && !state?.revision.isNullOrBlank()) {
                return vcsInfoFromUrl.copy(revision = state!!.revision!!)
            }
            if (vcsInfoFromUrl.revision.isBlank() && !state?.version.isNullOrBlank()) {
                return vcsInfoFromUrl.copy(revision = state!!.version!!)
            }
            return vcsInfoFromUrl
        }
    }

    private fun parseAuthorAndProjectFromRepo(repositoryURL: String): Pair<String?, String?> {
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

    private fun packageFromSPMDependency(spmDependency: SPMDependency): Package {
        requireNotNull(spmDependency.repositoryURL) {
            "Repository URL is missing. ${spmDependency.packageName} package is malformed"
        }

        val (author, project) = parseAuthorAndProjectFromRepo(spmDependency.repositoryURL)

        return Package(
            id = Identifier(
                type = managerName,
                namespace = author.orEmpty(),
                name = project ?: spmDependency.packageName,
                version = spmDependency.state?.toString() ?: UNKNOWN,
            ),
            description = "",
            binaryArtifact = EMPTY,
            sourceArtifact = EMPTY,
            authors = sortedSetOf(author),
            vcs = spmDependency.toVCSInfo(),
            declaredLicenses = sortedSetOf(), // SPM files don't provide License info. Leave this step for scanner.
            homepageUrl = spmDependency.repositoryURL.removeSuffix(".git")
        )
    }

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> {
        val mapper = ObjectMapper()
        val dependenciesJSON = mapper.readTree(definitionFile.readText()).get("object")?.get("pins")
        val dependencies = dependenciesJSON?.map { json -> mapper.treeToValue(json, SPMDependency::class.java) }
        val packages = dependencies?.mapNotNull { packageFromSPMDependency(spmDependency = it) }?.toSortedSet()

        val vcsInfo = VersionControlSystem.forDirectory(definitionFile.parentFile)?.getInfo().orEmpty()
        val (author, project) = parseAuthorAndProjectFromRepo(repositoryURL = vcsInfo.url)

        val projectIdentifier = Identifier(
            type = managerName,
            version = vcsInfo.revision,
            namespace = author.orEmpty(),
            name = project ?: definitionFile.parentFile.relativeTo(analysisRoot).invariantSeparatorsPath,
        )

        return listOf(
            ProjectAnalyzerResult(
                project = Project(
                    vcs = VcsInfo.EMPTY,
                    id = projectIdentifier,
                    authors = sortedSetOf(),
                    declaredLicenses = sortedSetOf(),
                    scopeDependencies = sortedSetOf(),
                    homepageUrl = normalizeVcsUrl(vcsInfo.url),
                    vcsProcessed = processProjectVcs(definitionFile.parentFile),
                    definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
                ),
                packages = packages ?: sortedSetOf()
            )
        )
    }
}
