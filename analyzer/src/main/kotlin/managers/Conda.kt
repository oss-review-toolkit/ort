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

import com.fasterxml.jackson.databind.node.ArrayNode

import com.here.ort.analyzer.AbstractPackageManagerFactory
import com.here.ort.analyzer.HTTP_CACHE_PATH
import com.here.ort.analyzer.PackageManager
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.EMPTY_JSON_NODE
import com.here.ort.model.Identifier
import com.here.ort.model.Package
import com.here.ort.model.PackageReference
import com.here.ort.model.Project
import com.here.ort.model.ProjectAnalyzerResult
import com.here.ort.model.Scope
import com.here.ort.model.VcsInfo
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.model.jsonMapper
import com.here.ort.utils.CommandLineTool
import com.here.ort.utils.Os
import com.here.ort.utils.OkHttpClientHelper
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.log
import com.here.ort.utils.safeDeleteRecursively
import com.here.ort.utils.showStackTrace
import com.here.ort.utils.textValueOrEmpty
import com.here.ort.utils.stripLeadingZerosFromVersion

import java.io.File
import java.io.IOException
import java.lang.IllegalArgumentException
import java.net.HttpURLConnection
import java.util.SortedSet

import okhttp3.Request

const val DEP_REVISION = "license-and-classifiers"

/**
 * The [Conda](https://docs.conda.io/en/latest/) package manager for Python.
 */
class Conda(
    name: String,
    analyzerRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analyzerRoot, analyzerConfig, repoConfig), CommandLineTool {
    class Factory : AbstractPackageManagerFactory<Conda>("CONDA") {
        override val globsForDefinitionFiles = listOf("requirements*.txt", "environment.yml", "setup.py")

        override fun create(
            analyzerRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Conda(managerName, analyzerRoot, analyzerConfig, repoConfig)
    }

    // create an environment with conda named "ort"
    // TODO: make this a more unique environment name
    private val envName = "ort"

    override fun command(workingDir: File?) = "conda"

    private fun runInEnv(envDir: File, commandName: String, vararg commandArgs: String):
            ProcessCapture {
        val binDir = if (Os.isWindows) "Scripts" else "bin"
        var command = File(envDir, binDir + File.separator + commandName)

        if (Os.isWindows && command.extension.isEmpty()) {
            // On Windows specifying the extension is optional, so try them in order.
            val extensions = System.getenv("PATHEXT")?.splitToSequence(File.pathSeparatorChar) ?: emptySequence()
            val commandWin = extensions.map { File(command.path + it.toLowerCase()) }.find { it.isFile }
            if (commandWin != null) {
                command = commandWin
            }
        }

        val process = ProcessCapture(command.path, *commandArgs)
        log.debug { process.stdout }
        return process
    }

    override fun resolveDependencies(definitionFile: File): ProjectAnalyzerResult? {
        // For an overview, dependency resolution involves the following steps:
        // 1. Install dependencies via conda
        // 2. Get meta-data about the local project via pydep (only for setup.py-based projects).
        // 3. Get the hierarchy of dependencies via pipdeptree.
        // 4. Get additional remote package meta-data via PyPIJSON.

        // working directory is the one containing the environment.yml
        // TODO: add support for environment.yml being in a different directory that setup.py
        val workingDir = definitionFile.parentFile
        val envDir = setupEnv(definitionFile)

        // List all packages installed locally in the environment.
        val pipdeptree = runInEnv(envDir,"pipdeptree", "-l", "--json-tree")

        // Install pydep after running any other command but before looking at the dependencies because it
        // downgrades pip to version 7.1.2. Use it to get meta-information from about the project from setup.py. As
        // pydep is not on PyPI, install it from Git instead.
        val pydepUrl = "git+https://github.com/heremaps/pydep@$DEP_REVISION"
        val conda = if (Os.isWindows) {
            // On Windows, in-place pip up- / downgrades require pip to be wrapped by "python -m", see
            // https://github.com/pypa/pip/issues/1299.
            runInEnv(
                envDir,"python", "-m", command(workingDir),
                *TRUSTED_HOSTS, "install", pydepUrl
            )
        } else {
            runInEnv(envDir, "pip", "install", pydepUrl)
        }
        conda.requireSuccess()

        var declaredLicenses: SortedSet<String> = sortedSetOf()

        // First try to get meta-data from "setup.py" in any case, even for "requirements.txt" projects.
        val (setupName, setupVersion, setupHomepage) = if (File(workingDir, "setup.py").isFile) {
            val pydep = if (Os.isWindows) {
                // On Windows, the script itself is not executable, so we need to wrap the call by "python".
                runInEnv(
                    envDir, "python",
                    envDir.path + "\\Scripts\\pydep-run.py", "info", "."
                )
            } else {
                runInEnv(envDir,"pydep-run.py", "info", ".")
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
            Pair(false, true) -> requirementsName
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
                    it["package_name"].textValue() in PIPDEPTREE_DEPENDENCIES
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
                    val body = response.body()?.string()?.trim()

                    if (response.code() != HttpURLConnection.HTTP_OK || body.isNullOrEmpty()) {
                        log.warn { "Unable to retrieve PyPI meta-data for package '${pkg.id.toCoordinates()}'." }
                        if (body != null) {
                            log.warn { "The response was '$body' (code ${response.code()})." }
                        }

                        // Fall back to returning the original package data.
                        return@use pkg
                    }

                    val pkgData = try {
                        jsonMapper.readTree(body)!!
                    } catch (e: IOException) {
                        e.showStackTrace()

                        log.warn {
                            "Unable to parse PyPI meta-data for package '${pkg.id.toCoordinates()}': ${e.message}"
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
                            vcsProcessed = processPackageVcs(pkg.vcs, pkgHomepage)
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
            vcsProcessed = processProjectVcs(workingDir, homepageUrl = setupHomepage),
            homepageUrl = setupHomepage,
            scopes = scopes
        )

        // Remove the env by simply deleting the directory.
        envDir.safeDeleteRecursively()

        return ProjectAnalyzerResult(project, packages.map { it.toCuratedPackage() }.toSortedSet())
    }



    private fun setupEnv(definitionFile: File): File {
        // Create an out-of-tree environment
        log.info { "Creating a conda env for $definitionFile..." }
        val envDir = createEnv(definitionFile)
        val install = installDependencies(definitionFile, envDir)

        if (install.isError) {
            log.debug {
                // pip writes the real error message to stdout instead of stderr.
                "First try to install dependencies using conda failed with:\n${install.stdout}"
            }

            if (install.isError) {
                // pip writes the real error message to stdout instead of stderr.
                throw IOException(install.stdout)
            }
        }

        log.info {
            "Successfully installed dependencies for project '$definitionFile' using conda."
        }

        return envDir
    }

    /**
     * Create a conda environment given the environment.yml or requirements.txt
     */
    private fun createEnv(envDefinition: File): File {
        ProcessCapture("conda", "env", "create", "--force", "--name", envName, "--file", envDefinition.absolutePath).requireSuccess()
        val envs = ProcessCapture("conda", "env", "list").requireSuccess()
        val res = envs.stdout.split('\n')
            .filter{ it.split("\\s".toRegex()).first() == envName }.first()
            .split("\\s".toRegex()).last()
        return File(res)
    }

    private fun installDependencies(definitionFile: File, envDir: File): ProcessCapture {
        // Install pipdeptree inside the conda environment as that's the only way to make it report only the project's
        // dependencies instead of those of all (globally) installed packages, see
        // https://github.com/naiquevin/pipdeptree#known-issues.
        // We only depend on pipdeptree to be at least version 0.5.0 for JSON output, but we stick to a fixed
        // version to be sure to get consistent results.
        var conda = runInEnv(envDir,"pip", *TRUSTED_HOSTS, "install", "pipdeptree==$PIPDEPTREE_VERSION")
        conda.requireSuccess()

        // TODO: Find a way to make installation of packages with native extensions work on Windows where often
        // the appropriate compiler is missing / not set up, e.g. by using pre-built packages from
        // http://www.lfd.uci.edu/~gohlke/pythonlibs/
        conda = if (definitionFile.name == "setup.py") {
            // Note that this only installs required "install" dependencies, not "extras" or "tests" dependencies.
            runInEnv(envDir,"pip", *TRUSTED_HOSTS, "install", *INSTALL_OPTIONS, ".")
        } else if (definitionFile.name == "environment.yml") {
            // use conda update
            ProcessCapture("conda", "env", "update", "--name", envName, "--file", definitionFile.absolutePath).requireSuccess()
        } else {
            // In "setup.py"-speak, "requirements.txt" just contains required "install" dependencies.
            runInEnv(
                envDir,"pip", *TRUSTED_HOSTS, "install", *INSTALL_OPTIONS, "-r",
                definitionFile.name
            )
        }

        // TODO: Consider logging a warning instead of an error if the command is run on a file that likely belongs
        // to a test.
        with(conda) {
            if (isError) {
                log.error { errorMessage }
            }
        }

        return conda
    }
}
