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

import ch.frankel.slf4k.*

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode

import com.here.ort.analyzer.Main
import com.here.ort.analyzer.PackageManager
import com.here.ort.analyzer.PackageManagerFactory
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.HashAlgorithm
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
import com.here.ort.utils.OS
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.checkCommandVersion
import com.here.ort.utils.log
import com.here.ort.utils.safeDeleteRecursively

import com.vdurmont.semver4j.Requirement

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.util.SortedSet

import okhttp3.Request

class PIP : PackageManager() {
    companion object : PackageManagerFactory<PIP>(
            "https://pip.pypa.io/",
            "Python",
            // See https://packaging.python.org/discussions/install-requires-vs-requirements/ and
            // https://caremad.io/posts/2013/07/setup-vs-requirement/.
            listOf("requirements*.txt", "setup.py")
    ) {
        override fun create() = PIP()

        private const val PIP_VERSION = "9.0.3"

        private const val PIPDEPTREE_VERSION = "0.12.1"
        private val PIPDEPTREE_DEPENDENCIES = arrayOf("pipdeptree", "setuptools", "wheel")

        private const val PYDEP_REVISION = "license-and-classifiers"
    }

    // TODO: Need to replace this hard-coded list of domains with e.g. a command line option.
    private val TRUSTED_HOSTS = listOf(
            "pypi.org",
            "pypi.python.org" // Legacy
    ).flatMap { listOf("--trusted-host", it) }.toTypedArray()

    override fun command(workingDir: File) = "pip"

    override fun toString() = PIP.toString()

    private fun runPipInVirtualEnv(virtualEnvDir: File, workingDir: File, vararg commandArgs: String) =
            runInVirtualEnv(virtualEnvDir, workingDir, command(workingDir), *TRUSTED_HOSTS, *commandArgs)

    private fun runInVirtualEnv(virtualEnvDir: File, workingDir: File, commandName: String, vararg commandArgs: String)
            : ProcessCapture {
        val binDir = if (OS.isWindows) "Scripts" else "bin"
        var command = File(virtualEnvDir, binDir + File.separator + commandName)

        if (OS.isWindows && command.extension.isEmpty()) {
            // On Windows specifying the extension is optional, so try them in order.
            val extensions = System.getenv("PATHEXT")?.splitToSequence(File.pathSeparatorChar) ?: emptySequence()
            val commandWin = extensions.map { File(command.path + it.toLowerCase()) }.find { it.isFile }
            if (commandWin != null) {
                command = commandWin
            }
        }

        // TODO: Maybe work around long shebang paths in generated scripts within a virtualenv by calling the Python
        // executable in the virtualenv directly, see https://github.com/pypa/virtualenv/issues/997.
        val process = ProcessCapture(workingDir, command.path, *commandArgs)
        log.debug { process.stdout() }
        return process
    }

    override fun prepareResolution(definitionFiles: List<File>): List<File> {
        // virtualenv bundles pip. In order to get pip 9.0.1 inside a virtualenv, which is a version that supports
        // installing packages from a Git URL that include a commit SHA1, we need at least virtualenv 15.1.0.
        checkCommandVersion("virtualenv", Requirement.buildIvy("15.1.+"), ignoreActualVersion = Main.ignoreVersions)

        return definitionFiles
    }

    override fun resolveDependencies(definitionFile: File): ProjectAnalyzerResult? {
        // For an overview, dependency resolution involves the following steps:
        // 1. Install dependencies via pip (inside a virtualenv, for isolation from globally installed packages).
        // 2. Get meta-data about the local project via pydep (only for setup.py-based projects).
        // 3. Get the hierarchy of dependencies via pipdeptree.
        // 4. Get additional remote package meta-data via PyPIJSON.

        val workingDir = definitionFile.parentFile
        val virtualEnvDir = setupVirtualEnv(workingDir, definitionFile)

        // List all packages installed locally in the virtualenv.
        val pipdeptree = runInVirtualEnv(virtualEnvDir, workingDir, "pipdeptree", "-l", "--json-tree")

        // Install pydep after running any other command but before looking at the dependencies because it
        // downgrades pip to version 7.1.2. Use it to get meta-information from about the project from setup.py. As
        // pydep is not on PyPI, install it from Git instead.
        val pydepUrl = "git+https://github.com/heremaps/pydep@$PYDEP_REVISION"
        val pip = if (OS.isWindows) {
            // On Windows, in-place pip up- / downgrades require pip to be wrapped by "python -m", see
            // https://github.com/pypa/pip/issues/1299.
            runInVirtualEnv(virtualEnvDir, workingDir, "python", "-m", command(workingDir),
                    *TRUSTED_HOSTS, "install", pydepUrl)
        } else {
            runPipInVirtualEnv(virtualEnvDir, workingDir, "install", pydepUrl)
        }
        pip.requireSuccess()

        var declaredLicenses: SortedSet<String> = sortedSetOf<String>()

        val (projectName, projectVersion, projectHomepage) = if (File(workingDir, "setup.py").isFile) {
            val pydep = if (OS.isWindows) {
                // On Windows, the script itself is not executable, so we need to wrap the call by "python".
                runInVirtualEnv(virtualEnvDir, workingDir, "python",
                        virtualEnvDir.path + "\\Scripts\\pydep-run.py", "info", ".")
            } else {
                runInVirtualEnv(virtualEnvDir, workingDir, "pydep-run.py", "info", ".")
            }
            pydep.requireSuccess()

            // What pydep actually returns as "repo_url" is either setup.py's
            // - "url", denoting the "home page for the package", or
            // - "download_url", denoting the "location where the package may be downloaded".
            // So the best we can do is to map this the project's homepage URL.
            jsonMapper.readTree(pydep.stdout()).let {
                declaredLicenses = getDeclaredLicenses(it)
                listOf(it["project_name"].asText(), it["version"].asText(), it["repo_url"].asText())
            }
        } else {
            // In case of "requirements*.txt" there is no meta-data at all available, so use the parent directory name
            // as the project name.
            listOf(definitionFile.parentFile.name, "", "")
        }

        val packages = sortedSetOf<Package>()
        val installDependencies = sortedSetOf<PackageReference>()

        if (pipdeptree.isSuccess()) {
            val fullDependencyTree = jsonMapper.readTree(pipdeptree.stdout())

            val projectDependencies = if (definitionFile.name == "setup.py") {
                // The tree contains a root node for the project itself and pipdeptree's dependencies are also at the
                // root next to it, as siblings.
                fullDependencyTree.find {
                    it["package_name"].asText() == projectName
                }?.get("dependencies")
                        ?: throw IOException("pipdeptree output does not contain the project dependencies.")
            } else {
                // The tree does not contain a node for the project itself. Its dependencies are on the root level
                // together with the dependencies of pipdeptree itself, which we need to filter out.
                fullDependencyTree.filterNot {
                    it["package_name"].asText() in PIPDEPTREE_DEPENDENCIES
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

                OkHttpClientHelper.execute(Main.HTTP_CACHE_PATH, pkgRequest).use { response ->
                    val body = response.body()?.string()?.trim()

                    if (response.code() != HttpURLConnection.HTTP_OK || body.isNullOrEmpty()) {
                        log.warn { "Unable to retrieve PyPI meta-data for package '${pkg.id}'." }
                        if (body != null) {
                            log.warn { "Response was '$body'." }
                        }

                        // Fall back to returning the original package data.
                        return@use pkg
                    }

                    val pkgData = try {
                        jsonMapper.readTree(body)!!
                    } catch (e: IOException) {
                        log.warn { "Unable to parse PyPI meta-data for package '${pkg.id}': ${e.message}" }

                        // Fall back to returning the original package data.
                        return@use pkg
                    }

                    try {
                        val pkgInfo = pkgData["info"]

                        val pkgDescription = pkgInfo["summary"]?.asText() ?: pkg.description
                        val pkgHomepage = pkgInfo["home_page"]?.asText() ?: pkg.homepageUrl
                        val pkgReleases = pkgData["releases"][pkg.id.version] as ArrayNode

                        // Amend package information with more details.
                        Package(
                                id = pkg.id,
                                declaredLicenses = getDeclaredLicenses(pkgInfo),
                                description = pkgDescription,
                                homepageUrl = pkgHomepage,
                                binaryArtifact = getBinaryArtifact(pkg, pkgReleases),
                                sourceArtifact = getSourceArtifact(pkgReleases),
                                vcs = pkg.vcs,
                                vcsProcessed = processPackageVcs(pkg.vcs, pkgHomepage)
                        )
                    } catch (e: NullPointerException) {
                        log.warn { "Unable to parse PyPI meta-data for package '${pkg.id}': ${e.message}" }

                        // Fall back to returning the original package data.
                        pkg
                    }
                }
            }
        } else {
            log.error {
                "Unable to determine dependencies for project in directory '$workingDir':\n${pipdeptree.stderr()}"
            }
        }

        // TODO: Handle "extras" and "tests" dependencies.
        val scopes = sortedSetOf(
                Scope("install", true, installDependencies)
        )

        val project = Project(
                id = Identifier(
                        provider = toString(),
                        namespace = "",
                        name = projectName,
                        version = projectVersion
                ),
                definitionFilePath = VersionControlSystem.getPathToRoot(definitionFile) ?: "",
                declaredLicenses = declaredLicenses,
                aliases = emptyList(),
                vcs = VcsInfo.EMPTY,
                vcsProcessed = processProjectVcs(workingDir, homepageUrl = projectHomepage),
                homepageUrl = projectHomepage,
                scopes = scopes
        )

        // Remove the virtualenv by simply deleting the directory.
        virtualEnvDir.safeDeleteRecursively()

        return ProjectAnalyzerResult(true, project, packages.map { it.toCuratedPackage() }.toSortedSet())
    }

    private fun getBinaryArtifact(pkg: Package, pkgReleases: ArrayNode): RemoteArtifact {
        // Prefer python wheels and fall back to the first entry (probably a sdist).
        val pkgRelease = pkgReleases.asSequence().find {
            it["packagetype"].asText() == "bdist_wheel"
        } ?: pkgReleases[0]

        return RemoteArtifact(
                url = pkgRelease["url"]?.asText() ?: pkg.binaryArtifact.url,
                hash = pkgRelease["md5_digest"]?.asText() ?: pkg.binaryArtifact.hash,
                hashAlgorithm = HashAlgorithm.MD5
        )
    }

    private fun getSourceArtifact(pkgReleases: ArrayNode): RemoteArtifact {
        val pkgSources = pkgReleases.asSequence().filter {
            it["packagetype"].asText() == "sdist"
        }

        if (pkgSources.count() == 0) return RemoteArtifact.EMPTY

        val pkgSource = pkgSources.find {
            it["filename"].asText().endsWith(".tar.bz2")
        } ?: pkgSources.elementAt(0)

        val url = pkgSource["url"]?.asText() ?: return RemoteArtifact.EMPTY
        val hash = pkgSource["md5_digest"]?.asText() ?: return RemoteArtifact.EMPTY

        return RemoteArtifact(url, hash, HashAlgorithm.MD5)
    }

    private fun getDeclaredLicenses(pkgInfo: JsonNode): SortedSet<String> {
        val declaredLicenses = sortedSetOf<String>()

        // Use the top-level license field as well as the license classifiers as the declared licenses.
        setOf(pkgInfo["license"]).mapNotNullTo(declaredLicenses) {
            it?.asText()?.removeSuffix(" License")?.takeUnless { it.isBlank() || it == "UNKNOWN" }
        }

        // Example license classifier:
        // "License :: OSI Approved :: GNU Library or Lesser General Public License (LGPL)"
        pkgInfo["classifiers"]?.mapNotNullTo(declaredLicenses) {
            val classifier = it.asText().split(" :: ")
            if (classifier.first() == "License") {
                classifier.last().removeSuffix(" License")
            } else {
                null
            }
        }

        return declaredLicenses
    }

    private fun setupVirtualEnv(workingDir: File, definitionFile: File): File {
        // Create an out-of-tree virtualenv.
        println("Creating a virtualenv for the '${workingDir.name}' project directory...")
        val virtualEnvDir = createTempDir(workingDir.name.padEnd(3, '_'), "virtualenv")
        ProcessCapture(workingDir, "virtualenv", virtualEnvDir.path).requireSuccess()

        var pip: ProcessCapture

        // Ensure to have installed a version of pip that is know to work for us.
        pip = if (OS.isWindows) {
            // On Windows, in-place pip up- / downgrades require pip to be wrapped by "python -m", see
            // https://github.com/pypa/pip/issues/1299.
            runInVirtualEnv(virtualEnvDir, workingDir, "python", "-m", command(workingDir),
                    *TRUSTED_HOSTS, "install", "pip==$PIP_VERSION")
        } else {
            runPipInVirtualEnv(virtualEnvDir, workingDir, "install", "pip==$PIP_VERSION")
        }
        pip.requireSuccess()

        // Install pipdeptree inside the virtualenv as that's the only way to make it report only the project's
        // dependencies instead of those of all (globally) installed packages, see
        // https://github.com/naiquevin/pipdeptree#known-issues.
        // We only depend on pipdeptree to be at least version 0.5.0 for JSON output, but we stick to a fixed
        // version to be sure to get consistent results.
        pip = runPipInVirtualEnv(virtualEnvDir, workingDir, "install", "pipdeptree==$PIPDEPTREE_VERSION")
        pip.requireSuccess()

        // TODO: Find a way to make installation of packages with native extensions work on Windows where often
        // the appropriate compiler is missing / not set up, e.g. by using pre-built packages from
        // http://www.lfd.uci.edu/~gohlke/pythonlibs/
        println("Installing dependencies for the '${workingDir.name}' project directory...")
        pip = if (definitionFile.name == "setup.py") {
            // Note that this only installs required "install" dependencies, not "extras" or "tests" dependencies.
            runPipInVirtualEnv(virtualEnvDir, workingDir, "install", ".")
        } else {
            // In "setup.py"-speak, "requirements.txt" just contains required "install" dependencies.
            runPipInVirtualEnv(virtualEnvDir, workingDir, "install", "-r", definitionFile.name)
        }

        // TODO: Consider logging a warning instead of an error if the command is run on a file that likely belongs
        // to a test.
        with(pip) {
            if (isError()) {
                log.error { failMessage }
            }
        }

        return virtualEnvDir
    }

    private fun parseDependencies(dependencies: Iterable<JsonNode>,
                                  allPackages: SortedSet<Package>, installDependencies: SortedSet<PackageReference>) {
        dependencies.forEach { dependency ->
            val name = dependency["package_name"].asText()
            val version = dependency["installed_version"].asText()

            val pkg = Package(
                    id = Identifier(
                            provider = "PyPI",
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
            allPackages.add(pkg)

            val packageRef = pkg.toReference()
            installDependencies.add(packageRef)

            parseDependencies(dependency["dependencies"], allPackages, packageRef.dependencies)
        }
    }
}
