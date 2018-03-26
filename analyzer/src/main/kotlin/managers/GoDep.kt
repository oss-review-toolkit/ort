/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

import ch.frankel.slf4k.debug
import ch.frankel.slf4k.warn
import com.here.ort.analyzer.Main
import com.here.ort.analyzer.PackageManager
import com.here.ort.analyzer.PackageManagerFactory
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.*
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.log
import com.here.ort.utils.safeDeleteRecursively

import com.moandjiezana.toml.Toml

import java.io.File
import java.io.IOException

class GoDep : PackageManager() {
    companion object : PackageManagerFactory<GoDep>(
            "https://golang.github.io/dep/",
            "Go",
            // FIXME DRY names of legacy manifest files
            listOf("Gopkg.toml", "glide.yaml")
    ) {
        // TODO Add remaining supported legacy manifest files.
        private val LEGACY_MANIFESTS = mapOf("glide.yaml" to "glide.lock")
        override fun create() = GoDep()
    }

    override fun command(workingDir: File) = "dep"

    override fun resolveDependencies(definitionFile: File): AnalyzerResult? {
        // Normalize the path to avoid using "." as the name of the project when the analyzer is run with "-i .".
        val projectDir = definitionFile.parentFile.toPath().normalize().toFile()

        val gopath = createTempDir(projectDir.name.padEnd(3, '_'), "_gopath")
        val workingDir = setUpWorkspace(projectDir, gopath)

        if (definitionFile.name in LEGACY_MANIFESTS.keys) {
            importLegacyManifest(definitionFile, projectDir, workingDir, gopath)
        }

        val projects = parseProjects(workingDir, gopath)
        val packages: MutableList<Package> = mutableListOf()
        val packageRefs: MutableList<PackageReference> = mutableListOf()
        val provider = GoDep.toString()

        for (project in projects) {
            // parseProjects() made sure that all entries contain these keys
            val name = project["name"]!!
            val revision = project["revision"]!!
            val version = project["version"]!!

            val vcs = VcsInfo(provider, name, revision, "")
            var vcsProcessed = VcsInfo.EMPTY
            val errors: MutableList<String> = mutableListOf()

            try {
                vcsProcessed = resolveVcsInfo(vcs, gopath)
            } catch (e: IOException) {
                errors.add(e.toString())
            }

            val pkg = Package(
                    id = Identifier(provider, "", name, version),
                    declaredLicenses = sortedSetOf(),
                    description = "",
                    homepageUrl = "",
                    binaryArtifact = RemoteArtifact.EMPTY,
                    sourceArtifact = RemoteArtifact.EMPTY,
                    vcs = vcs,
                    vcsProcessed = vcsProcessed
            )

            packages.add(pkg)
            packageRefs.add(PackageReference("", pkg.id.name, pkg.id.version, sortedSetOf(), errors))
        }

        val scope = Scope("default", true, packageRefs.toSortedSet())
        val vcsProcessed = VersionControlSystem.forDirectory(projectDir)?.getInfo()?.normalize() ?: VcsInfo.EMPTY

        // TODO Keeping this between scans would speed things up considerably.
        gopath.safeDeleteRecursively()

        return AnalyzerResult(
                allowDynamicVersions = Main.allowDynamicVersions,
                project = Project(
                        id = Identifier(provider, "", projectDir.name, vcsProcessed.revision),
                        declaredLicenses = sortedSetOf(),
                        aliases = emptyList(),
                        vcs = VcsInfo.EMPTY,
                        vcsProcessed = vcsProcessed,
                        homepageUrl = "",
                        scopes = sortedSetOf(scope)
                ),
                packages = packages.toSortedSet()
        )
    }

    private fun importLegacyManifest(definitionFile: File, projectDir: File, workingDir: File, gopath: File) {
        val lockFile = File(workingDir, LEGACY_MANIFESTS[definitionFile.name])

        if (!lockFile.isFile && !Main.allowDynamicVersions) {
            throw IllegalArgumentException(
                    "No lockfile found in $projectDir, dependency versions are unstable.")
        }

        log.debug { "Running \"dep init\" to import legacy manifest file ${definitionFile.name}" }

        val env = mapOf("GOPATH" to gopath.absolutePath)
        ProcessCapture(workingDir, env, "dep", "init").requireSuccess()
    }

    private fun setUpWorkspace(projectDir: File, gopath: File): File {
        val destination = File(gopath, "src" + File.separator + projectDir.name)

        log.debug { "Copying $projectDir to temporary directory $destination" }

        projectDir.copyRecursively(destination)

        val dotGit = File(destination, ".git")
        if (dotGit.isFile) {
            // HACK "dep" seems to be confused by git submodules. We detect this by checking whether ".git" exists
            // and is a regular file instead of a directory.
            dotGit.delete()
        }

        return destination
    }

    private fun parseProjects(workingDir: File, gopath: File): List<Map<String, String>> {
        val lockFile = File(workingDir, "Gopkg.lock")
        if (!lockFile.isFile) {
            if (!Main.allowDynamicVersions) {
                throw IllegalArgumentException(
                        "No lockfile found in $workingDir, dependency versions are unstable.")
            }

            log.debug { "Running \"dep ensure\" to generate missing lockfile in $workingDir" }

            val env = mapOf("GOPATH" to gopath.absolutePath)
            ProcessCapture(workingDir, env, "dep", "ensure").requireSuccess()
        }

        val entries = Toml().read(lockFile).toMap()["projects"]
        if (entries == null) {
            log.warn { "${lockFile.name} is missing any [[projects]] entries" }
            return listOf()
        }

        val projects: MutableList<Map<String, String>> = mutableListOf()

        for (entry in entries as List<*>) {
            val project = entry as? Map<*, *> ?: continue
            val name = project["name"]
            val revision = project["revision"]

            if (name !is String || revision !is String) {
                log.warn { "Invalid [[projects]] entry in $lockFile: $entry" }
                continue
            }

            val version = project["version"] as? String ?: revision
            projects.add(mapOf("name" to name, "revision" to revision, "version" to version))
        }

        return projects
    }

    private fun resolveVcsInfo(vcs: VcsInfo, gopath: File): VcsInfo {
        val name = if (vcs.url.endsWith("/...")) {
            vcs.url
        } else {
            "${vcs.url}/..."
        }

        val env = mapOf("GOPATH" to gopath.absolutePath)
        ProcessCapture(null, env, "go", "get", "-d", name).requireSuccess()

        val pkgPath = "src" + File.separator + vcs.url.replace('/', File.separatorChar)
        val repoRoot = File(gopath, pkgPath)

        val deducedVcs = VersionControlSystem.forDirectory(repoRoot)
                ?.getInfo()?.normalize() ?: return VcsInfo.EMPTY

        // We want the revision recorded in Gopkg.lock, not the one "go get" fetched.
        return VcsInfo(deducedVcs.type, deducedVcs.url, vcs.revision, deducedVcs.path)
    }
}
