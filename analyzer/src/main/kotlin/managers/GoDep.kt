/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package com.here.ort.analyzer.managers

import ch.frankel.slf4k.*

import com.here.ort.analyzer.AbstractPackageManagerFactory
import com.here.ort.analyzer.PackageManager
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.Identifier
import com.here.ort.model.OrtIssue
import com.here.ort.model.Package
import com.here.ort.model.PackageLinkage
import com.here.ort.model.PackageReference
import com.here.ort.model.Project
import com.here.ort.model.ProjectAnalyzerResult
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.Scope
import com.here.ort.model.VcsInfo
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.utils.CommandLineTool
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.collectMessagesAsString
import com.here.ort.utils.log
import com.here.ort.utils.safeCopyRecursively
import com.here.ort.utils.safeDeleteRecursively
import com.here.ort.utils.showStackTrace

import com.moandjiezana.toml.Toml

import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.Paths

val GO_LEGACY_MANIFESTS = mapOf(
        "glide.yaml" to "glide.lock",
        "Godeps.json" to ""
)

/**
 * The Dep package manager for Go, see https://golang.github.io/dep/.
 */
class GoDep(name: String, analyzerConfig: AnalyzerConfiguration, repoConfig: RepositoryConfiguration) :
        PackageManager(name, analyzerConfig, repoConfig), CommandLineTool {
    class Factory : AbstractPackageManagerFactory<GoDep>("GoDep") {
        override val globsForDefinitionFiles = listOf("Gopkg.toml", *GO_LEGACY_MANIFESTS.keys.toTypedArray())

        override fun create(analyzerConfig: AnalyzerConfiguration, repoConfig: RepositoryConfiguration) =
                GoDep(managerName, analyzerConfig, repoConfig)
    }

    override fun command(workingDir: File?) = "dep"

    override fun resolveDependencies(definitionFile: File): ProjectAnalyzerResult? {
        val projectDir = resolveProjectRoot(definitionFile)
        val projectVcs = processProjectVcs(projectDir)
        val gopath = createTempDir("ort", "${projectDir.name}-gopath")
        val workingDir = setUpWorkspace(projectDir, projectVcs, gopath)

        GO_LEGACY_MANIFESTS[definitionFile.name]?.let { lockfileName ->
            log.debug { "Importing legacy manifest file at '$definitionFile'." }
            importLegacyManifest(lockfileName, workingDir, gopath)
        }

        val projects = parseProjects(workingDir, gopath)
        val packages = mutableListOf<Package>()
        val packageRefs = mutableListOf<PackageReference>()

        for (project in projects) {
            // parseProjects() made sure that all entries contain these keys
            val name = project.getValue("name")
            val revision = project.getValue("revision")
            val version = project.getValue("version")

            val errors = mutableListOf<OrtIssue>()

            val vcsProcessed = try {
                resolveVcsInfo(name, revision, gopath)
            } catch (e: IOException) {
                e.showStackTrace()

                log.error { "Could not resolve VCS information for project '$name': ${e.collectMessagesAsString()}" }

                errors += OrtIssue(source = managerName, message = e.collectMessagesAsString())
                VcsInfo.EMPTY
            }

            val pkg = Package(
                    id = Identifier(managerName, "", name, version),
                    declaredLicenses = sortedSetOf(),
                    description = "",
                    homepageUrl = "",
                    binaryArtifact = RemoteArtifact.EMPTY,
                    sourceArtifact = RemoteArtifact.EMPTY,
                    vcs = VcsInfo.EMPTY,
                    vcsProcessed = vcsProcessed
            )

            packages += pkg

            packageRefs += pkg.toReference(linkage = PackageLinkage.STATIC, errors = errors)
        }

        val scope = Scope("default", packageRefs.toSortedSet())

        // TODO Keeping this between scans would speed things up considerably.
        gopath.safeDeleteRecursively(force = true)

        return ProjectAnalyzerResult(
                project = Project(
                        id = Identifier(managerName, "", projectDir.name, projectVcs.revision),
                        definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
                        declaredLicenses = sortedSetOf(),
                        vcs = VcsInfo.EMPTY,
                        vcsProcessed = projectVcs,
                        homepageUrl = "",
                        scopes = sortedSetOf(scope)
                ),
                packages = packages.map { it.toCuratedPackage() }.toSortedSet()
        )
    }

    fun deduceImportPath(projectDir: File, vcs: VcsInfo, gopath: File): File =
            if (vcs == VcsInfo.EMPTY) {
                Paths.get(gopath.path, "src", projectDir.name)
            } else {
                val uri = URI(vcs.url)
                Paths.get(gopath.path, "src", uri.host, uri.path)
            }.toAbsolutePath().toFile()

    private fun resolveProjectRoot(definitionFile: File): File {
        val projectDir = when (definitionFile.name) {
            "Godeps.json" -> definitionFile.parentFile.parentFile
            else -> definitionFile.parentFile
        }

        // Normalize the path to avoid using "." as the name of the project when the analyzer is run with "-i .".
        return projectDir.toPath().normalize().toFile()
    }

    private fun importLegacyManifest(lockfileName: String, workingDir: File, gopath: File) {
        if (lockfileName.isNotEmpty() && !File(workingDir, lockfileName).isFile &&
                !analyzerConfig.allowDynamicVersions) {
            throw IllegalArgumentException("No lockfile found in ${workingDir.invariantSeparatorsPath}, dependency " +
                    "versions are unstable.")
        }

        run("init", workingDir = workingDir, environment = mapOf("GOPATH" to gopath.absolutePath))
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
        val lockfile = File(workingDir, "Gopkg.lock")
        if (!lockfile.isFile) {
            if (!analyzerConfig.allowDynamicVersions) {
                throw IllegalArgumentException(
                        "No lockfile found in ${workingDir.invariantSeparatorsPath}, dependency versions are unstable.")
            }

            log.debug { "Running 'dep ensure' to generate missing lockfile in $workingDir" }

            run("ensure", workingDir = workingDir, environment = mapOf("GOPATH" to gopath.absolutePath))
        }

        val entries = Toml().read(lockfile).toMap()["projects"]
        if (entries == null) {
            log.warn { "${lockfile.name} is missing any [[projects]] entries" }
            return listOf()
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
        val pc = ProcessCapture("go", "get", "-d", importPath, environment = mapOf("GOPATH" to gopath.absolutePath))

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

            if (!errorMessagesToIgnore.any { it in msg }) {
                throw IOException(msg)
            }
        }

        val repoRoot = Paths.get(gopath.path, "src", importPath).toFile()

        // We want the revision recorded in Gopkg.lock contained in "vcs", not the one "go get" fetched.
        return processProjectVcs(repoRoot).copy(revision = revision)
    }
}
