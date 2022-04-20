/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

import com.moandjiezana.toml.Toml

import java.io.File
import java.io.IOException
import java.net.URI

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.downloader.VcsHost
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtIssue
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
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.ProcessCapture
import org.ossreviewtoolkit.utils.common.collectMessagesAsString
import org.ossreviewtoolkit.utils.common.realFile
import org.ossreviewtoolkit.utils.common.safeCopyRecursively
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.common.toUri
import org.ossreviewtoolkit.utils.core.createOrtTempDir
import org.ossreviewtoolkit.utils.core.log
import org.ossreviewtoolkit.utils.core.showStackTrace

/**
 * A map of legacy package manager file names "dep" can import, and their respective lock file names, if any.
 */
private val GO_LEGACY_MANIFESTS = mapOf(
    // The [Glide](https://github.com/Masterminds/glide) package manager uses a dedicated `glide.yaml` rules file for
    // direct dependencies and scans dependent packages for imports to determine transitive dependencies.
    "glide.yaml" to "glide.lock",

    // The [godep](https://github.com/tools/godep) dependency manager works by inspecting imports but was discontinued
    // in favor of the [dep](https://github.com/golang/dep) dependency management tool.
    "Godeps.json" to ""
)

/**
 * The [Dep](https://golang.github.io/dep/) package manager for Go.
 */
class GoDep(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analysisRoot, analyzerConfig, repoConfig), CommandLineTool {
    class Factory : AbstractPackageManagerFactory<GoDep>("GoDep") {
        override val globsForDefinitionFiles = listOf("Gopkg.toml", *GO_LEGACY_MANIFESTS.keys.toTypedArray())

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = GoDep(managerName, analysisRoot, analyzerConfig, repoConfig)
    }

    override fun command(workingDir: File?) = "dep"

    override fun getVersionArguments() = "version"

    override fun transformVersion(output: String) =
        output.lineSequence().first { "version" in it }.substringAfter(':').trim().removePrefix("v")

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> {
        val projectDir = resolveProjectRoot(definitionFile)
        val projectVcs = processProjectVcs(projectDir)
        val gopath = createOrtTempDir("${projectDir.name}-gopath")
        val workingDir = setUpWorkspace(projectDir, projectVcs, gopath)

        GO_LEGACY_MANIFESTS[definitionFile.name]?.let { lockfileName ->
            log.debug { "Importing legacy manifest file at '$definitionFile'." }
            importLegacyManifest(lockfileName, workingDir, gopath)
        }

        val projects = parseProjects(workingDir, gopath)
        val packages = sortedSetOf<Package>()
        val packageRefs = mutableListOf<PackageReference>()

        for (project in projects) {
            // parseProjects() made sure that all entries contain these keys
            val name = project.getValue("name")
            val revision = project.getValue("revision")
            val version = project.getValue("version")

            val issues = mutableListOf<OrtIssue>()

            val vcsProcessed = try {
                resolveVcsInfo(name, revision, gopath)
            } catch (e: IOException) {
                e.showStackTrace()

                issues += createAndLogIssue(
                    source = managerName,
                    message = "Could not resolve VCS information for project '$name': ${e.collectMessagesAsString()}"
                )

                VcsInfo.EMPTY
            }

            val pkg = Package(
                id = Identifier(managerName, "", name, version),
                authors = sortedSetOf(),
                declaredLicenses = sortedSetOf(),
                description = "",
                homepageUrl = "",
                binaryArtifact = RemoteArtifact.EMPTY,
                sourceArtifact = RemoteArtifact.EMPTY,
                vcs = VcsInfo.EMPTY,
                vcsProcessed = vcsProcessed
            )

            packages += pkg

            packageRefs += pkg.toReference(linkage = PackageLinkage.STATIC, issues = issues)
        }

        val scope = Scope("default", packageRefs.toSortedSet())

        val projectName = runCatching {
            val uri = URI(projectVcs.url)
            val vcsPath = VersionControlSystem.getPathInfo(definitionFile.parentFile).path
            listOf(uri.host, uri.path.removePrefix("/").removeSuffix(".git"), vcsPath)
                .filterNot { it.isEmpty() }
                .joinToString(separator = "/")
                .lowercase()
        }.getOrDefault(projectDir.name)

        // TODO Keeping this between scans would speed things up considerably.
        gopath.safeDeleteRecursively(force = true)

        return listOf(
            ProjectAnalyzerResult(
                project = Project(
                    id = Identifier(
                        type = managerName,
                        namespace = "",
                        name = projectName,
                        version = projectVcs.revision
                    ),
                    definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
                    authors = sortedSetOf(),
                    declaredLicenses = sortedSetOf(),
                    vcs = VcsInfo.EMPTY,
                    vcsProcessed = projectVcs,
                    homepageUrl = "",
                    scopeDependencies = sortedSetOf(scope)
                ),
                packages = packages
            )
        )
    }

    fun deduceImportPath(projectDir: File, vcs: VcsInfo, gopath: File): File =
        gopath.resolve("src").let { src ->
            val uri = vcs.url.toUri().getOrNull()
            if (uri?.host != null) {
                src.resolve("${uri.host}/${uri.path}")
            } else {
                src.resolve(projectDir.name)
            }
        }

    private fun resolveProjectRoot(definitionFile: File) =
        when (definitionFile.name) {
            "Godeps.json" -> definitionFile.parentFile.parentFile
            else -> definitionFile.parentFile
        }

    private fun importLegacyManifest(lockfileName: String, workingDir: File, gopath: File) {
        requireLockfile(workingDir) { lockfileName.isEmpty() || workingDir.resolve(lockfileName).isFile }

        run("init", workingDir = workingDir, environment = mapOf("GOPATH" to gopath.realFile().path))
    }

    private fun setUpWorkspace(projectDir: File, vcs: VcsInfo, gopath: File): File {
        val destination = deduceImportPath(projectDir, vcs, gopath)

        log.debug { "Copying $projectDir to temporary directory $destination" }

        projectDir.safeCopyRecursively(destination)

        val dotGit = File(destination, ".git")
        if (dotGit.isFile) {
            // HACK "dep" seems to be confused by git submodules. We detect this by checking whether ".git" exists
            // and is a regular file instead of a directory.
            dotGit.delete()
        }

        return destination
    }

    private fun parseProjects(workingDir: File, gopath: File): List<Map<String, String>> {
        val lockfile = workingDir.resolve("Gopkg.lock")
        if (!lockfile.isFile) {
            require(analyzerConfig.allowDynamicVersions) {
                "No lockfile found in ${workingDir.invariantSeparatorsPath}, dependency versions are unstable."
            }

            log.debug { "Running 'dep ensure' to generate missing lockfile in $workingDir" }

            run("ensure", workingDir = workingDir, environment = mapOf("GOPATH" to gopath.path))
        }

        val entries = Toml().read(lockfile).toMap()["projects"]
        if (entries == null) {
            log.warn { "${lockfile.name} is missing any [[projects]] entries" }
            return emptyList()
        }

        val projects = mutableListOf<Map<String, String>>()

        for (entry in entries as List<*>) {
            val project = entry as? Map<*, *> ?: continue
            val name = project["name"]
            val revision = project["revision"]

            if (name !is String || revision !is String) {
                log.warn { "Invalid [[projects]] entry in $lockfile: $entry" }
                continue
            }

            val version = project["version"] as? String ?: revision
            projects += mapOf("name" to name, "revision" to revision, "version" to version)
        }

        return projects
    }

    private fun resolveVcsInfo(importPath: String, revision: String, gopath: File): VcsInfo {
        val pc = ProcessCapture(
            "go", "get", "-d", importPath,
            environment = mapOf("GOPATH" to gopath.path, "GO111MODULE" to "off")
        )

        // HACK Some failure modes from "go get" can be ignored:
        // 1. repositories that don't have .go files in the root directory
        // 2. all files in the root directory have certain "build constraints" (like "// +build ignore")
        if (pc.isError) {
            val msg = pc.stderr

            val errorMessagesToIgnore = listOf(
                "no Go files in",
                "no buildable Go source files in",
                "build constraints exclude all Go files in"
            )

            if (!errorMessagesToIgnore.any { it in msg }) throw IOException(msg)
        }

        val repoRoot = gopath.resolve("src/$importPath")

        // The "processProjectVcs()" function should always be able to deduce VCS information from the working tree
        // created by "go get". However, if that fails for whatever reason, fall back to guessing VCS information from
        // the "importPath" (which usually resembles a URL).
        val fallbackVcsInfo = VcsHost.parseUrl("https://$importPath").takeIf {
            it.type != VcsType.UNKNOWN
        } ?: VcsInfo.EMPTY

        // We want the revision recorded in Gopkg.lock contained in "vcs", not the one "go get" fetched.
        return processProjectVcs(repoRoot, fallbackVcsInfo).copy(revision = revision)
    }
}
