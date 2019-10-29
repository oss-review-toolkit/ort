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

package com.here.ort.analyzer.managers.utils

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode

import com.here.ort.analyzer.HTTP_CACHE_PATH
import com.here.ort.analyzer.PackageManager
import com.here.ort.analyzer.managers.Pip
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.EMPTY_JSON_NODE
import com.here.ort.model.Hash
import com.here.ort.model.Identifier
import com.here.ort.model.Package
import com.here.ort.model.PackageReference
import com.here.ort.model.Project
import com.here.ort.model.ProjectAnalyzerResult
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.Scope
import com.here.ort.model.VcsInfo
import com.here.ort.model.jsonMapper
import com.here.ort.utils.OkHttpClientHelper
import com.here.ort.utils.Os
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.collectMessagesAsString
import com.here.ort.utils.log
import com.here.ort.utils.showStackTrace
import com.here.ort.utils.stripLeadingZerosFromVersion
import com.here.ort.utils.textValueOrEmpty

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.util.SortedSet

import okhttp3.Request

abstract class PythonSupport(val managerName: String, val analysisRoot: File, val command: String, val envDir: File) {
    companion object {
        private const val PYDEP_REVISION = "license-and-classifiers"
        private const val PYDEP_URL = "git+https://github.com/heremaps/pydep@$PYDEP_REVISION"

        private val PHONY_DEPENDENCIES = mapOf(
            "pipdeptree" to "", // A dependency of pipdeptree itself.
            "pkg-resources" to "0.0.0", // Added by a bug with some Ubuntu distributions.
            "setuptools" to "", // A dependency of pipdeptree itself.
            "wheel" to "" // A dependency of pipdeptree itself.
        )

        private fun isPhonyDependency(name: String, version: String): Boolean =
            PHONY_DEPENDENCIES[name].orEmpty().let { ignoredVersion ->
                PHONY_DEPENDENCIES.containsKey(name) && (ignoredVersion.isEmpty() || version == ignoredVersion)
            }
    }

    abstract fun runInEnv(
        workingDir: File,
        commandName: String,
        vararg commandArgs: String
    ): ProcessCapture

    fun resolveDependencies(definitionFile: File): ProjectAnalyzerResult? {
        // For an overview, dependency resolution involves the following steps:
        // 1. Install dependencies via pip (inside a virtualenv, for isolation from globally installed packages).
        // 2. Get meta-data about the local project via pydep (only for setup.py-based projects).
        // 3. Get the hierarchy of dependencies via pipdeptree.
        // 4. Get additional remote package meta-data via PyPIJSON.

        val workingDir = definitionFile.parentFile

        // List all packages installed locally in the virtualenv.
        val pipdeptree = runInEnv(workingDir, "pipdeptree", "-l", "--json-tree")

        // Install pydep after running any other command but before looking at the dependencies because it
        // downgrades pip to version 7.1.2. Use it to get meta-information from about the project from setup.py. As
        // pydep is not on PyPI, install it from Git instead.
        val pip = if (Os.isWindows) {
            // On Windows, in-place pip up-/downgrades require pip to be wrapped by "python -m", see
            // https://github.com/pypa/pip/issues/1299.
            runInEnv(
                workingDir, "python", "-m", command,
                *Pip.TRUSTED_HOSTS, "install", PYDEP_URL
            )
        } else {
            runInEnv(workingDir, "pip", *Pip.TRUSTED_HOSTS, "install", PYDEP_URL)
        }
        pip.requireSuccess()

        var declaredLicenses: SortedSet<String> = sortedSetOf<String>()

        // First try to get meta-data from "setup.py" in any case, even for "requirements.txt" projects.
        val (setupName, setupVersion, setupHomepage) = if (File(workingDir, "setup.py").isFile) {
            val pydep = if (Os.isWindows) {
                // On Windows, the script itself is not executable, so we need to wrap the call by "python".
                runInEnv(workingDir, "python", envDir.path + "\\Scripts\\pydep-run.py", "info", ".")
            } else {
                runInEnv(workingDir, "pydep-run.py", "info", ".")
            }
            pydep.requireSuccess()

            // What pydep actually returns as "repo_url" is either setup.py's
            // - "url", denoting the "home page for the package", or
            // - "download_url", denoting the "location where the package may be downloaded".
            // So the best we can do is to map this the project's homepage URL.
            jsonMapper.readTree(pydep.stdout).let {
                declaredLicenses = getDeclaredLicenses(it)
                listOf(
                    it["project_name"].textValue(), it["version"].textValueOrEmpty(),
                    it["repo_url"].textValueOrEmpty()
                )
            }
        } else {
            listOf("", "", "")
        }

        // Try to get additional information from any "requirements.txt" file.
        val (requirementsName, requirementsVersion, requirementsSuffix) = if (definitionFile.name != "setup.py") {
            val pythonVersionLines = definitionFile.readLines().filter { it.contains("python_version") }
            if (pythonVersionLines.isNotEmpty()) {
                log.debug {
                    "Some dependencies have Python version requirements:\n$pythonVersionLines"
                }
            }

            // In case of "requirements*.txt" there is no meta-data at all available, so use the parent directory name
            // plus what "*" expands to as the project name and the VCS revision, if any, as the project version.
            val suffix = definitionFile.name.removePrefix("requirements").removeSuffix(".txt")
            val name = definitionFile.parentFile.name + suffix

            val version = VersionControlSystem.getCloneInfo(workingDir).revision

            listOf(name, version, suffix)
        } else {
            listOf("", "", "")
        }

        // Amend information from "setup.py" with that from "requirements.txt".
        val projectName = when (Pair(setupName.isNotEmpty(), requirementsName.isNotEmpty())) {
            Pair(true, false) -> setupName
            // In case of only a requirements file without further meta-data, use the relative path to the analyzer
            // root as a unique project name.
            Pair(false, true) -> definitionFile.relativeTo(analysisRoot).invariantSeparatorsPath
            Pair(true, true) -> "$setupName-requirements$requirementsSuffix"
            else -> throw IllegalArgumentException("Unable to determine a project name for '$definitionFile'.")
        }
        val projectVersion = setupVersion.takeIf { it.isNotEmpty() } ?: requirementsVersion

        val packages = sortedSetOf<Package>()
        val installDependencies = sortedSetOf<PackageReference>()

        if (pipdeptree.isSuccess) {
            val fullDependencyTree = jsonMapper.readTree(pipdeptree.stdout)

            val projectDependencies = if (definitionFile.name == "setup.py") {
                // The tree contains a root node for the project itself and pipdeptree's dependencies are also at the
                // root next to it, as siblings.
                fullDependencyTree.find {
                    it["package_name"].textValue() == projectName
                }?.get("dependencies") ?: run {
                    log.info { "The '$projectName' project does not declare any dependencies." }
                    EMPTY_JSON_NODE
                }
            } else {
                // The tree does not contain a node for the project itself. Its dependencies are on the root level
                // together with the dependencies of pipdeptree itself, which we need to filter out.
                fullDependencyTree.filterNot {
                    isPhonyDependency(it["package_name"].textValue(), it["installed_version"].textValueOrEmpty())
                }
            }

            val packageTemplates = sortedSetOf<Package>()
            parseDependencies(projectDependencies, packageTemplates, installDependencies)

            // Enrich the package templates with additional meta-data from PyPI.
            packageTemplates.mapTo(packages) { pkg ->
                // See https://wiki.python.org/moin/PyPIJSON.
                val pkgRequest = Request.Builder()
                    .get()
                    .url("https://pypi.org/pypi/${pkg.id.name}/${pkg.id.version}/json")
                    .build()

                OkHttpClientHelper.execute(HTTP_CACHE_PATH, pkgRequest).use { response ->
                    val body = response.body?.string()?.trim()

                    if (response.code != HttpURLConnection.HTTP_OK || body.isNullOrEmpty()) {
                        log.warn { "Unable to retrieve PyPI meta-data for package '${pkg.id.toCoordinates()}'." }
                        if (body != null) {
                            log.warn { "The response was '$body' (code ${response.code})." }
                        }

                        // Fall back to returning the original package data.
                        return@use pkg
                    }

                    val pkgData = try {
                        jsonMapper.readTree(body)!!
                    } catch (e: IOException) {
                        e.showStackTrace()

                        log.warn {
                            "Unable to parse PyPI meta-data for package '${pkg.id.toCoordinates()}': " +
                                    e.collectMessagesAsString()
                        }

                        // Fall back to returning the original package data.
                        return@use pkg
                    }

                    pkgData["info"]?.let { pkgInfo ->
                        val pkgDescription = pkgInfo["summary"]?.textValue() ?: pkg.description
                        val pkgHomepage = pkgInfo["home_page"]?.textValue() ?: pkg.homepageUrl

                        val pkgRelease = pkgData["releases"]?.let { pkgReleases ->
                            val pkgVersion = pkgReleases.fieldNames().asSequence().find {
                                stripLeadingZerosFromVersion(it) == pkg.id.version
                            }

                            pkgReleases[pkgVersion]
                        } as? ArrayNode

                        // Amend package information with more details.
                        Package(
                            id = pkg.id,
                            declaredLicenses = getDeclaredLicenses(pkgInfo),
                            description = pkgDescription,
                            homepageUrl = pkgHomepage,
                            binaryArtifact = if (pkgRelease != null) {
                                getBinaryArtifact(pkg, pkgRelease)
                            } else {
                                pkg.binaryArtifact
                            },
                            sourceArtifact = if (pkgRelease != null) {
                                getSourceArtifact(pkgRelease)
                            } else {
                                pkg.sourceArtifact
                            },
                            vcs = pkg.vcs,
                            vcsProcessed = PackageManager.processPackageVcs(pkg.vcs, pkgHomepage)
                        )
                    } ?: run {
                        log.warn {
                            "PyPI meta-data for package '${pkg.id.toCoordinates()}' does not provide any information."
                        }

                        // Fall back to returning the original package data.
                        pkg
                    }
                }
            }
        } else {
            log.error {
                "Unable to determine dependencies for project in directory '$workingDir':\n${pipdeptree.stderr}"
            }
        }

        // TODO: Handle "extras" and "tests" dependencies.
        val scopes = sortedSetOf(
            Scope("install", installDependencies)
        )

        val project = Project(
            id = Identifier(
                type = managerName,
                namespace = "",
                name = projectName,
                version = projectVersion
            ),
            definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
            declaredLicenses = declaredLicenses,
            vcs = VcsInfo.EMPTY,
            vcsProcessed = PackageManager.processProjectVcs(workingDir, homepageUrl = setupHomepage),
            homepageUrl = setupHomepage,
            scopes = scopes
        )

        return ProjectAnalyzerResult(project, packages.mapTo(sortedSetOf()) { it.toCuratedPackage() })
    }

    /**
     * Get declared licenses from PIP package JSON metadata.
     */
    fun getDeclaredLicenses(pkgInfo: JsonNode): SortedSet<String> {
        val declaredLicenses = sortedSetOf<String>()

        // Use the top-level license field as well as the license classifiers as the declared licenses.
        setOf(pkgInfo["license"]).mapNotNullTo(declaredLicenses) { license ->
            license?.textValue()?.removeSuffix(" License")?.takeUnless { it.isBlank() || it == "UNKNOWN" }
        }

        // Example license classifier:
        // "License :: OSI Approved :: GNU Library or Lesser General Public License (LGPL)"
        pkgInfo["classifiers"]?.mapNotNullTo(declaredLicenses) { it ->
            val classifier = it.textValue().split(" :: ")
            classifier.takeIf { it.first() == "License" }?.last()?.removeSuffix(" License")
        }

        return declaredLicenses
    }

    fun parseDependencies(
        dependencies: Iterable<JsonNode>,
        allPackages: SortedSet<Package>,
        installDependencies: SortedSet<PackageReference>
    ) {
        dependencies.forEach { dependency ->
            val name = dependency["package_name"].textValue()
            val version = dependency["installed_version"].textValue()

            val pkg = Package(
                id = Identifier(
                    type = "PyPI",
                    namespace = "",
                    name = name,
                    version = version
                ),
                declaredLicenses = sortedSetOf(),
                description = "",
                homepageUrl = "",
                binaryArtifact = RemoteArtifact.EMPTY,
                sourceArtifact = RemoteArtifact.EMPTY,
                vcs = VcsInfo.EMPTY
            )
            allPackages += pkg

            val packageRef = pkg.toReference()
            installDependencies += packageRef

            parseDependencies(dependency["dependencies"], allPackages, packageRef.dependencies)
        }
    }

    fun getBinaryArtifact(pkg: Package, releaseNode: ArrayNode): RemoteArtifact {
        // Prefer python wheels and fall back to the first entry (probably a sdist).
        val binaryArtifact = releaseNode.asSequence().find {
            it["packagetype"].textValue() == "bdist_wheel"
        } ?: releaseNode[0]

        val url = binaryArtifact["url"]?.textValue() ?: pkg.binaryArtifact.url
        val hash = binaryArtifact["md5_digest"]?.textValue()?.let { Hash.create(it) } ?: pkg.binaryArtifact.hash

        return RemoteArtifact(url, hash)
    }

    fun getSourceArtifact(releaseNode: ArrayNode): RemoteArtifact {
        val sourceArtifacts = releaseNode.asSequence().filter {
            it["packagetype"].textValue() == "sdist"
        }

        if (sourceArtifacts.count() == 0) return RemoteArtifact.EMPTY

        val sourceArtifact = sourceArtifacts.find {
            it["filename"].textValue().endsWith(".tar.bz2")
        } ?: sourceArtifacts.elementAt(0)

        val url = sourceArtifact["url"]?.textValue() ?: return RemoteArtifact.EMPTY
        val hash = sourceArtifact["md5_digest"]?.textValue() ?: return RemoteArtifact.EMPTY

        return RemoteArtifact(url, Hash.create(hash))
    }
}
