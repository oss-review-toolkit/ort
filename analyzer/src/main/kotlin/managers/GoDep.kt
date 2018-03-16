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

import com.here.ort.analyzer.Main
import com.here.ort.analyzer.PackageManager
import com.here.ort.analyzer.PackageManagerFactory
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.*
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.safeDeleteRecursively

import com.moandjiezana.toml.Toml

import java.io.File
import java.io.IOException

class GoDep : PackageManager() {
    companion object : PackageManagerFactory<GoDep>(
            "https://golang.github.io/dep/",
            "Go",
            // TODO add filenames of manifests for supported legacy tools and import them with "dep init"
            listOf("Gopkg.toml")
    ) {
        override fun create() = GoDep()
    }

    override fun command(workingDir: File) = "dep"

    override fun resolveDependencies(definitionFile: File): AnalyzerResult? {
        val workingDir = definitionFile.parentFile

        // FIXME
        // Handle missing lock file either with an error or by running "dep ensure"
        // (when allowDynamicVersions is set).
        val lockFile = File(workingDir, "Gopkg.lock")

        val projects = Toml().read(lockFile).toMap()["projects"] as? List<*> ?: return null
        val packages: MutableList<Package> = mutableListOf()
        val packageRefs: MutableList<PackageReference> = mutableListOf()
        val gopath = createTempDir(workingDir.name.padEnd(3, '_'), "gopath")
        val provider = GoDep.toString()

        for (project in projects) {
            if (project !is Map<*, *>)
                continue  // TODO log warning?

            val name = project["name"] as? String ?: ""
            val revision = project["revision"] as? String ?: ""
            val version = project["version"] as? String ?: revision
            val vcs = VcsInfo(provider, name, revision, "")

            var vcsProcessed = VcsInfo.EMPTY
            var errors: MutableList<String> = mutableListOf()

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

        val vcsProcessed = VersionControlSystem.forDirectory(workingDir)
                ?.getInfo()?.normalize() ?: VcsInfo.EMPTY

        // TODO Keeping this between scans would speed things up considerably.
        gopath.safeDeleteRecursively()

        return AnalyzerResult(
                allowDynamicVersions = Main.allowDynamicVersions,
                project = Project(
                        id = Identifier(provider, "", workingDir.name, vcsProcessed.revision),
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

    private fun resolveVcsInfo(vcs: VcsInfo, gopath: File): VcsInfo {

        val name = if (vcs.url.endsWith("/...")) {
            vcs.url
        } else {
            "${vcs.url}/..."
        }

        val env = mapOf("GOPATH" to gopath.absolutePath)
        ProcessCapture(gopath, env, "go", "get", "-d", name).requireSuccess()

        val pkgPath = "src" + File.separator + vcs.url.replace('/', File.separatorChar)
        val repoRoot = File(gopath, pkgPath)

        val deducedVcs = VersionControlSystem.forDirectory(repoRoot)
                ?.getInfo()?.normalize() ?: return VcsInfo.EMPTY

        // We want the revision recorded in Gopkg.lock, not the one "go get" fetched.
        return VcsInfo(deducedVcs.type, deducedVcs.url, vcs.revision, deducedVcs.path)
    }
}
