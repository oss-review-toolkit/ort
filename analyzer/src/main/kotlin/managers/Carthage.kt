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

import com.fasterxml.jackson.module.kotlin.readValue

import java.io.File
import java.net.URL
import java.util.SortedSet

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.model.orEmpty
import org.ossreviewtoolkit.utils.common.unquote
import org.ossreviewtoolkit.utils.core.normalizeVcsUrl

/**
 * The [Carthage](https://github.com/Carthage/Carthage) package manager for Objective-C / Swift.
 */
class Carthage(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analysisRoot, analyzerConfig, repoConfig) {
    class Factory : AbstractPackageManagerFactory<Carthage>("Carthage") {
        // TODO: Add support for the Cartfile.
        //       This would require to resolve the actual dependency versions as a Cartfile supports dynamic versions.
        override val globsForDefinitionFiles = listOf("Cartfile.resolved")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Carthage(managerName, analysisRoot, analyzerConfig, repoConfig)
    }

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> {
        // Transitive dependencies are only supported if the dependency itself uses Carthage.
        // See: https://github.com/Carthage/Carthage#nested-dependencies
        val workingDir = definitionFile.parentFile
        val projectInfo = getProjectInfoFromVcs(workingDir)

        return listOf(
            ProjectAnalyzerResult(
                project = Project(
                    id = Identifier(
                        type = managerName,
                        namespace = projectInfo.namespace.orEmpty(),
                        name = projectInfo.projectName.orEmpty(),
                        version = projectInfo.revision.orEmpty()
                    ),
                    definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
                    authors = sortedSetOf(),
                    declaredLicenses = sortedSetOf(),
                    vcs = VcsInfo.EMPTY,
                    vcsProcessed = processProjectVcs(workingDir, VcsInfo.EMPTY),
                    scopeDependencies = sortedSetOf(),
                    homepageUrl = ""
                ),
                packages = parseCarthageDependencies(definitionFile)
            )
        )
    }

    /**
     * As the "Carthage.resolved" file does not provide any project information, trying to retrieve some from VCS.
     */
    private fun getProjectInfoFromVcs(workingDir: File): ProjectInfo {
        val workingTree = VersionControlSystem.forDirectory(workingDir)
        val vcsInfo = workingTree?.getInfo().orEmpty()
        val normalizedVcsUrl = normalizeVcsUrl(vcsInfo.url)
        val vcsHost = VcsHost.fromUrl(normalizedVcsUrl)

        return ProjectInfo(
            namespace = vcsHost?.getUserOrOrganization(normalizedVcsUrl),
            projectName = vcsHost?.getProject(normalizedVcsUrl)
                ?: workingDir.relativeTo(analysisRoot).invariantSeparatorsPath,
            revision = vcsInfo.revision
        )
    }

    private fun parseCarthageDependencies(definitionFile: File): SortedSet<Package> {
        val dependencyLines = definitionFile.readLines()
        val workingDir = definitionFile.parent
        val packages = sortedSetOf<Package>()

        dependencyLines.forEach { line ->
            if (line.isBlank() || line.isComment()) return@forEach
            packages += parseDependencyLine(line, workingDir)
        }

        return packages
    }

    private fun parseDependencyLine(line: String, workingDir: String): Package {
        val split = line.split(' ')

        require(split.size == 3) {
            "A dependency line must consist of exactly 3 space separated elements."
        }

        val type = DependencyType.valueOf(split[0].uppercase())
        val id = split[1].unquote()
        val revision = split[2].unquote()

        return when (type) {
            DependencyType.GITHUB -> {
                // ID consists of github username/project or a github enterprise URL.
                val projectUrl = if (id.split('/').size == 2) {
                    val (username, project) = id.split("/", limit = 2)
                    "https://github.com/$username/$project"
                } else {
                    id
                }

                createPackageFromGenericGitUrl(projectUrl, revision)
            }

            DependencyType.GIT -> {
                // ID is a generic git URL (https, git or ssh) or a file path to a local repository.
                if (isFilePath(workingDir, id)) {
                    // TODO: Carthage supports local git repositories.
                    //       The downloader should be extended to support `file://` downloads.
                    createPackageFromLocalRepository(id, revision)
                } else {
                    createPackageFromGenericGitUrl(id, revision)
                }
            }

            DependencyType.BINARY -> {
                // ID is an URL or a path to a file that contains a Carthage binary project specification.
                val binarySpecString = if (isFilePath(workingDir, id)) {
                    val filePath = id.removePrefix("file://")
                    val binarySpecFile = when {
                        File(filePath).isAbsolute -> File(filePath)
                        else -> File("$workingDir/$filePath")
                    }
                    binarySpecFile.readText()
                } else {
                    URL(id).readText()
                }

                val binarySpec = jsonMapper.readValue<Map<String, String>>(binarySpecString)
                createPackageFromBinarySpec(binarySpec, id, revision)
            }
        }
    }

    private fun createPackageFromGenericGitUrl(projectUrl: String, revision: String): Package {
        val vcsInfoFromUrl = VcsHost.parseUrl(projectUrl)
        val vcsInfo = vcsInfoFromUrl.copy(revision = revision)
        val vcsHost = VcsHost.fromUrl(vcsInfo.url)

        return Package(
            id = Identifier(
                type = managerName,
                namespace = vcsHost?.getUserOrOrganization(projectUrl).orEmpty(),
                name = vcsHost?.getProject(projectUrl).orEmpty(),
                version = revision
            ),
            authors = sortedSetOf(),
            declaredLicenses = sortedSetOf(),
            description = "",
            homepageUrl = projectUrl.removeSuffix(".git"),
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = RemoteArtifact.EMPTY,
            vcs = vcsInfo
        )
    }

    private fun createPackageFromLocalRepository(fileUrl: String, revision: String): Package {
        val vcsInfo = VcsInfo(
            type = VcsType.GIT,
            url = fileUrl,
            revision = revision
        )

        return Package(
            id = Identifier(
                type = managerName,
                namespace = "",
                name = fileUrl.substringAfterLast("/"),
                version = revision
            ),
            authors = sortedSetOf(),
            declaredLicenses = sortedSetOf(),
            description = "",
            homepageUrl = "",
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = RemoteArtifact.EMPTY,
            vcs = vcsInfo
        )
    }

    private fun createPackageFromBinarySpec(binarySpec: Map<String, String>, id: String, revision: String) =
        Package(
            id = Identifier(
                type = managerName,
                namespace = "",
                name = id.substringAfterLast("/").removeSuffix(".json"),
                version = revision
            ),
            authors = sortedSetOf(),
            declaredLicenses = sortedSetOf(),
            description = "",
            homepageUrl = "",
            binaryArtifact = RemoteArtifact(
                url = binarySpec[revision].orEmpty(),
                hash = Hash.NONE
            ),
            sourceArtifact = RemoteArtifact.EMPTY,
            vcs = VcsInfo.EMPTY
        )

    private fun isFilePath(workingDir: String, path: String) =
        // This covers the two cases supported by Carthage, where either the path start with "file://" and points to a
        // local Git repository, or the path is a local binary specification JSON.
        path.startsWith("file://") || File(path).exists() || File("$workingDir/$path").exists()
}

private enum class DependencyType {
    /**
     * Dependencies that originate from GitHub, see
     * https://github.com/Carthage/Carthage/blob/master/Documentation/Artifacts.md#github-repositories
     */
    GITHUB,

    /**
     * Dependencies that originate from generic Git repositories, see
     * https://github.com/Carthage/Carthage/blob/master/Documentation/Artifacts.md#git-repositories
     */
    GIT,

    /**
     * Dependencies that are only available as compiled binary frameworks, see
     * https://github.com/Carthage/Carthage/blob/master/Documentation/Artifacts.md#binary-only-frameworks
     */
    BINARY
}

private data class ProjectInfo(val namespace: String?, val projectName: String?, val revision: String?)

private fun String.isComment() = trim().startsWith("#")
