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
import com.here.ort.model.Package
import com.here.ort.model.PackageReference
import com.here.ort.model.Project
import com.here.ort.model.AnalyzerResult
import com.here.ort.model.HashAlgorithm
import com.here.ort.model.Identifier
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.Scope
import com.here.ort.model.VcsInfo
import com.here.ort.utils.OkHttpClientHelper
import com.here.ort.utils.OS
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.checkCommandVersion
import com.here.ort.utils.jsonMapper
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

        private const val PIPDEPTREE_VERSION = "0.10.1"
        private const val PYDEP_REVISION = "ea18b40fca03438a0fb362e552c26df2d29fc19f"

        private val PIPDEPTREE_TOP_LEVEL_REGEX = Regex("([\\w-]+).*")
        private val PIPDEPTREE_DEPENDENCIES = arrayOf("pipdeptree", "setuptools", "wheel")
    }

    // TODO: Need to replace this hard-coded list of domains with e.g. a command line option.
    private val TRUSTED_HOSTS = listOf(
            "pypi.python.org"
    ).flatMap { listOf("--trusted-host", it) }.toTypedArray()

    override fun command(workingDir: File) = "pip"

    private fun runPipInVirtualEnv(virtualEnvDir: File, workingDir: File, vararg commandArgs: String) =
            runInVirtualEnv(virtualEnvDir, workingDir, command(workingDir), *TRUSTED_HOSTS, *commandArgs)

    private fun runInVirtualEnv(virtualEnvDir: File, workingDir: File, commandName: String, vararg commandArgs: String)
            : ProcessCapture {
        val binDir = if (OS.isWindows) "Scripts" else "bin"
        var command = File(virtualEnvDir, binDir + File.separator + commandName)

        if (OS.isWindows && command.extension.isEmpty()) {
            // On Windows specifying the extension is optional, so try them in order.
            val extensions = System.getenv("PATHEXT").split(File.pathSeparator)
            val commandWin = extensions.asSequence().map { File(command.path + it.toLowerCase()) }.find { it.isFile }
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
        checkCommandVersion("virtualenv", Requirement.buildStrict("15.1.0"), ignoreActualVersion = Main.ignoreVersions)

        return definitionFiles
    }

    override fun resolveDependencies(projectDir: File, workingDir: File, definitionFile: File): AnalyzerResult? {
        // For an overview, dependency resolution involves the following steps:
        // 1. Install dependencies via pip (inside a virtualenv, for isolation from globally installed packages).
        // 2. Get meta-data about the local project via pydep (only for setup.py-based projects).
        // 3. Get the hierarchy of dependencies via pipdeptree.
        // 4. Get additional remote package meta-data via PyPIJSON.

        val virtualEnvDir = setupVirtualEnv(workingDir, definitionFile)

        // List all packages installed locally in the virtualenv. As only the plain text pipdeptree output shows the
        // hierarchy of dependencies, but the JSON output is easier to parse, we unfortunately need both.
        val pipdeptree = runInVirtualEnv(virtualEnvDir, workingDir, "pipdeptree", "-l")
        val pipdeptreeJson = runInVirtualEnv(virtualEnvDir, workingDir, "pipdeptree", "-l", "--json")

        // Install pydep after running any other command but before looking at the dependencies because it
        // downgrades pip to version 7.1.2. Use it to get meta-information from about the project from setup.py. As
        // pydep is not on PyPI, install it from Git instead.
        val pydepUrl = "git+https://github.com/sourcegraph/pydep@$PYDEP_REVISION"
        val pip = if (OS.isWindows) {
            // On Windows, in-place pip up- / downgrades require pip to be wrapped by "python -m", see
            // https://github.com/pypa/pip/issues/1299.
            runInVirtualEnv(virtualEnvDir, workingDir, "python", "-m", command(workingDir),
                    *TRUSTED_HOSTS, "install", pydepUrl)
        } else {
            runPipInVirtualEnv(virtualEnvDir, workingDir, "install", pydepUrl)
        }
        pip.requireSuccess()

        val (projectName, projectVersion, projectHomepage) = if (definitionFile.name == "setup.py") {
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
                listOf(it["project_name"].asText(), it["version"].asText(), it["repo_url"].asText())
            }
        } else {
            // In case of a requirements.txt file without meta-data, use the parent directory name as the project name.
            listOf(definitionFile.parentFile.name, "", "")
        }

        val packages = sortedSetOf<Package>()
        val installDependencies = sortedSetOf<PackageReference>()

        if (pipdeptreeJson.exitValue() == 0) {
            val allDependencies = jsonMapper.readTree(pipdeptreeJson.stdout()) as ArrayNode

            if (definitionFile.name != "setup.py" && pipdeptree.exitValue() == 0) {
                val topLevelDependencies = pipdeptree.stdout().lines().mapNotNull {
                    PIPDEPTREE_TOP_LEVEL_REGEX.matchEntire(it)?.groupValues?.get(1).takeUnless {
                        it in PIPDEPTREE_DEPENDENCIES
                    }
                }

                // Put in a fake root dependency for the project itself.
                val projectData = jsonMapper.createObjectNode()
                projectData.putObject("package").put("package_name", projectName)

                val projectDependencies = projectData.putArray("dependencies")
                topLevelDependencies.forEach { name ->
                    val dependency = jsonMapper.createObjectNode()
                    dependency.put("package_name", name)

                    val version = allDependencies.asSequence().map {
                        it["package"]
                    }.find {
                        it["package_name"].asText() == name
                    }?.get("installed_version")?.asText()
                    dependency.put("installed_version", version)

                    projectDependencies.add(dependency)
                }

                allDependencies.add(projectData)
            }

            val packageTemplates = sortedSetOf<Package>()
            parseDependencies(projectName, allDependencies, packageTemplates, installDependencies)

            packageTemplates.mapTo(packages) { pkg ->
                // See https://wiki.python.org/moin/PyPIJSON.
                val pkgRequest = Request.Builder()
                        .get()
                        .url("https://pypi.python.org/pypi/${pkg.id.name}/${pkg.id.version}/json")
                        .build()

                OkHttpClientHelper.execute(HTTP_CACHE_PATH, pkgRequest).use { response ->
                    val body = response.body()?.string()?.trim()

                    if (response.code() != HttpURLConnection.HTTP_OK || body.isNullOrBlank()) {
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
                        var pkgReleases = pkgData["releases"][pkg.id.version] as ArrayNode
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

                        // Amend package information with more details.
                        Package(
                                id = pkg.id,
                                declaredLicenses = declaredLicenses,
                                description = pkgInfo["summary"]?.asText() ?: pkg.description,
                                homepageUrl = pkgInfo["home_page"]?.asText() ?: pkg.homepageUrl,
                                binaryArtifact = getBinaryArtifact(pkg, pkgReleases),
                                sourceArtifact = getSourceArtifact(pkgReleases),
                                vcs = pkg.vcs,
                                vcsProcessed = processPackageVcs(pkg.vcs)
                        )
                    } catch (e: NullPointerException) {
                        log.warn { "Unable to parse PyPI meta-data for package '${pkg.id}': ${e.message}" }

                        // Fall back to returning the original package data.
                        pkg
                    }
                }
            }
        } else {
            log.error { "Unable to determine dependencies for project in directory '$workingDir'." }
        }

        // TODO: Handle "extras" and "tests" dependencies.
        val scopes = sortedSetOf(
                Scope("install", true, installDependencies)
        )

        val project = Project(
                id = Identifier(
                        packageManager = javaClass.simpleName,
                        namespace = "",
                        name = projectName,
                        version = projectVersion
                ),
                declaredLicenses = sortedSetOf(), // TODO: Get the licenses for local projects.
                aliases = emptyList(),
                vcs = VcsInfo.EMPTY,
                vcsProcessed = processProjectVcs(projectDir),
                homepageUrl = projectHomepage,
                scopes = scopes
        )

        // Remove the virtualenv by simply deleting the directory.
        virtualEnvDir.safeDeleteRecursively()

        return AnalyzerResult(true, project, packages)
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
        val pkgSource = pkgReleases.asSequence().find {
            it["packagetype"].asText() == "sdist"
        } ?: return RemoteArtifact.EMPTY

        val url = pkgSource["url"]?.asText() ?: return RemoteArtifact.EMPTY
        val hash = pkgSource["md5_digest"]?.asText() ?: return RemoteArtifact.EMPTY

        return RemoteArtifact(url, hash, HashAlgorithm.MD5)
    }

    private fun setupVirtualEnv(workingDir: File, definitionFile: File): File {
        // Create an out-of-tree virtualenv.
        println("Creating a virtualenv for the '${workingDir.name}' project directory...")
        val virtualEnvDir = createTempDir(workingDir.name.padEnd(3, '_'), "virtualenv")
        ProcessCapture(workingDir, "virtualenv", virtualEnvDir.path).requireSuccess()

        var pip: ProcessCapture

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
            if (exitValue() != 0) {
                log.error { failMessage }
            }
        }

        return virtualEnvDir
    }

    private fun parseDependencies(rootPackageName: String, allDependencies: Iterable<JsonNode>,
                                  packages: SortedSet<Package>, installDependencies: SortedSet<PackageReference>) {
        // With JSON output enabled pipdeptree does not really return a tree but a list of all dependencies:
        // [
        //     {
        //         "dependencies": [],
        //         "package": {
        //             "installed_version": "1.16",
        //             "package_name": "patch",
        //             "key": "patch"
        //         }
        //     },
        //     {
        //         "dependencies": [
        //             {
        //                 "required_version": null,
        //                 "installed_version": "36.5.0",
        //                 "package_name": "setuptools",
        //                 "key": "setuptools"
        //             }
        //         ],
        //         "package": {
        //             "installed_version": "1.2.1",
        //             "package_name": "zc.lockfile",
        //             "key": "zc.lockfile"
        //         }
        //     }
        // ]

        val packageData = allDependencies.find { it["package"]["package_name"].asText() == rootPackageName }
        if (packageData == null) {
            log.error { "No package data found for '$rootPackageName'." }
            return
        }

        val packageDependencies = packageData["dependencies"]
        packageDependencies.forEach {
            val packageName = it["package_name"].asText()
            val packageVersion = it["installed_version"].asText()

            val dependencyPackage = Package(
                    id = Identifier(
                            packageManager = javaClass.simpleName,
                            namespace = "",
                            name = packageName,
                            version = packageVersion
                    ),
                    declaredLicenses = sortedSetOf(),
                    description = "",
                    homepageUrl = "",
                    binaryArtifact = RemoteArtifact.EMPTY,
                    sourceArtifact = RemoteArtifact.EMPTY,
                    vcs = VcsInfo.EMPTY
            )
            packages.add(dependencyPackage)

            val packageRef = dependencyPackage.toReference()
            installDependencies.add(packageRef)

            parseDependencies(packageName, allDependencies, packages, packageRef.dependencies)
        }
    }
}
