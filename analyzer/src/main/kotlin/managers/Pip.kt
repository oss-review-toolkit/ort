/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import java.io.File
import java.util.SortedSet

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.managers.utils.PythonInspector
import org.ossreviewtoolkit.analyzer.managers.utils.toOrtPackages
import org.ossreviewtoolkit.analyzer.managers.utils.toPackageReferences
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.PackageManagerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.ort.showStackTrace

object Python : CommandLineTool, Logging {
    override fun command(workingDir: File?) = if (Os.isWindows) "py" else "python3"

    override fun transformVersion(output: String) = output.removePrefix("Python ")
}

private const val OPTION_OPERATING_SYSTEM = "operatingSystem"
private const val OPTION_OPERATING_SYSTEM_DEFAULT = "linux"
private val OPERATING_SYSTEMS = listOf("linux", "mac", "windows")

private const val OPTION_PYTHON_VERSION = "pythonVersion"
private const val OPTION_PYTHON_VERSION_DEFAULT = "3.10"
private val PYTHON_VERSIONS = listOf("2.7", "3.6", "3.7", "3.8", "3.9", "3.10")

/**
 * The [PIP](https://pip.pypa.io/) package manager for Python. Also see
 * [install_requires vs requirements files](https://packaging.python.org/discussions/install-requires-vs-requirements/)
 * and [setup.py vs. requirements.txt](https://caremad.io/posts/2013/07/setup-vs-requirement/).
 *
 * This package manager supports the following [options][PackageManagerConfiguration.options]:
 * - *operatingSystem*: The name of the operating system to resolve dependencies for. One of "linux", "mac", or
 *   "windows". Defaults to "linux".
 * - *pythonVersion*: The Python version to resolve dependencies for. Defaults to "3.10".
 */
@Suppress("TooManyFunctions")
class Pip(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analysisRoot, analyzerConfig, repoConfig) {
    companion object : Logging {
        private const val SHORT_STRING_MAX_CHARS = 200
    }

    class Factory : AbstractPackageManagerFactory<Pip>("PIP") {
        override val globsForDefinitionFiles = listOf("*requirements*.txt", "setup.py")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Pip(managerName, analysisRoot, analyzerConfig, repoConfig)
    }

    private val operatingSystemOption = (options[OPTION_OPERATING_SYSTEM] ?: OPTION_OPERATING_SYSTEM_DEFAULT)
        .also { os ->
            require(os.isEmpty() || os in OPERATING_SYSTEMS) {
                "The 'operatingSystem' option must be one of ${OPERATING_SYSTEMS.joinToString { "'$it'" }}."
            }
        }

    private val pythonVersionOption = (options[OPTION_PYTHON_VERSION] ?: OPTION_PYTHON_VERSION_DEFAULT)
        .also { pythonVersion ->
            require(pythonVersion in PYTHON_VERSIONS) {
                "The 'pythonVersion' option must be one of ${PYTHON_VERSIONS.joinToString { "'$it'" }}."
            }
        }

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> {
        // For an overview, dependency resolution involves the following steps:
        // 1. Get metadata about the local project via `python setup.py`.
        // 2. Get the dependency tree and dependency metadata via python-inspector.

        val project = getProjectBasics(definitionFile)
        val (packages, installDependencies) = getInstallDependencies(definitionFile)

        // TODO: Handle "extras" and "tests" dependencies.
        val scopes = sortedSetOf(
            Scope("install", installDependencies)
        )

        return listOf(ProjectAnalyzerResult(project.copy(scopeDependencies = scopes), packages))
    }

    private fun getProjectBasics(definitionFile: File): Project {
        val authors = sortedSetOf<String>()
        val declaredLicenses = sortedSetOf<String>()

        val workingDir = definitionFile.parentFile

        // First try to get metadata from "setup.py" in any case, even for "requirements.txt" projects.
        val (setupName, setupVersion, setupHomepage) = if (workingDir.resolve("setup.py").isFile) {
            // See https://docs.python.org/3.8/distutils/setupscript.html#additional-meta-data.
            fun getSetupPyMetadata(option: String): String? {
                val process = Python.run(workingDir, "setup.py", option)
                val metadata = process.stdout.trim()
                return metadata.takeUnless { process.isError || metadata == "UNKNOWN" }
            }

            parseAuthorString(getSetupPyMetadata("--author")).also { authors += it }
            getLicenseFromLicenseField(getSetupPyMetadata("--license"))?.also { declaredLicenses += it }
            getSetupPyMetadata("--classifiers")?.lines()?.mapNotNullTo(declaredLicenses) {
                getLicenseFromClassifier(it)
            }

            listOf(
                getSetupPyMetadata("--name").orEmpty(),
                getSetupPyMetadata("--version").orEmpty(),
                getSetupPyMetadata("--url").orEmpty()
            )
        } else {
            listOf("", "", "")
        }

        // Try to get additional information from any "requirements.txt" file.
        val (requirementsName, requirementsVersion, requirementsSuffix) = if (definitionFile.name != "setup.py") {
            val pythonVersionLines = definitionFile.readLines().filter { "python_version" in it }
            if (pythonVersionLines.isNotEmpty()) {
                logger.debug {
                    "Some dependencies have Python version requirements:\n$pythonVersionLines"
                }
            }

            // In case of "requirements*.txt" there is no metadata at all available, so use the parent directory name
            // plus what "*" expands to as the project name and the VCS revision, if any, as the project version.
            val suffix = definitionFile.name.removePrefix("requirements").removeSuffix(".txt")
            val name = definitionFile.parentFile.name + suffix

            val version = VersionControlSystem.getCloneInfo(workingDir).revision

            listOf(name, version, suffix)
        } else {
            listOf("", "", "")
        }

        // Amend information from "setup.py" with that from "requirements.txt".
        val hasSetupName = setupName.isNotEmpty()
        val hasRequirementsName = requirementsName.isNotEmpty()

        val projectName = when {
            hasSetupName && !hasRequirementsName -> setupName
            // In case of only a requirements file without further metadata, use the relative path to the analyzer
            // root as a unique project name.
            !hasSetupName && hasRequirementsName -> definitionFile.relativeTo(analysisRoot).invariantSeparatorsPath
            hasSetupName && hasRequirementsName -> "$setupName-requirements$requirementsSuffix"
            else -> throw IllegalArgumentException("Unable to determine a project name for '$definitionFile'.")
        }
        val projectVersion = setupVersion.takeIf { it.isNotEmpty() } ?: requirementsVersion

        return Project(
            id = Identifier(
                type = managerName,
                namespace = "",
                name = projectName,
                version = projectVersion
            ),
            definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
            authors = authors,
            declaredLicenses = declaredLicenses,
            vcs = VcsInfo.EMPTY,
            vcsProcessed = processProjectVcs(workingDir, VcsInfo.EMPTY, setupHomepage),
            homepageUrl = setupHomepage
        )
    }

    private fun getInstallDependencies(definitionFile: File): Pair<SortedSet<Package>, SortedSet<PackageReference>> {
        val workingDir = definitionFile.parentFile

        logger.info {
            "Resolving dependencies for '${definitionFile.absolutePath}' with Python version '$pythonVersionOption' " +
                    "and operating system '$operatingSystemOption'."
        }

        val pythonInspectorResult = runCatching {
            try {
                PythonInspector.run(
                    workingDir = workingDir,
                    definitionFile = definitionFile,
                    pythonVersion = pythonVersionOption.replace(".", ""),
                    operatingSystem = operatingSystemOption
                )
            } finally {
                workingDir.resolve(".cache").safeDeleteRecursively(force = true)
            }
        }.onFailure { e ->
            e.showStackTrace()

            logger.error {
                "Unable to determine dependencies for definition file '${definitionFile.absolutePath}': " +
                        e.collectMessages()
            }
        }.getOrThrow()

        val packages = pythonInspectorResult.packages.toOrtPackages()
        val packageReferences = pythonInspectorResult.resolvedDependencies.toPackageReferences()

        return packages to packageReferences
    }

    private fun parseAuthorString(author: String?): SortedSet<String> =
        author?.takeIf(::isValidAuthor)?.let { sortedSetOf(it) } ?: sortedSetOf()

    /**
     * Check if the given [author] string represents a valid author name. There are some non-null strings that
     * indicate that no author information is available. For instance, setup.py files can contain empty strings;
     * the "pip show" command prints the string "None" in this case.
     */
    private fun isValidAuthor(author: String): Boolean = author.isNotBlank() && author != "None"

    private fun getLicenseFromLicenseField(value: String?): String? {
        if (value.isNullOrBlank() || value == "UNKNOWN") return null

        // See https://docs.python.org/3/distutils/setupscript.html#additional-meta-data for what a "short string" is.
        val isShortString = value.length <= SHORT_STRING_MAX_CHARS && value.lines().size == 1
        if (!isShortString) return null

        // Apply a work-around for projects that declare licenses in classifier-syntax in the license field.
        return getLicenseFromClassifier(value) ?: value
    }

    private fun getLicenseFromClassifier(classifier: String): String? {
        // Example license classifier (also see https://pypi.org/classifiers/):
        // "License :: OSI Approved :: GNU Library or Lesser General Public License (LGPL)"
        val classifiers = classifier.split(" :: ").map { it.trim() }
        val licenseClassifiers = listOf("License", "OSI Approved")
        val license = classifiers.takeIf { it.first() in licenseClassifiers }?.last()
        return license?.takeUnless { it in licenseClassifiers }
    }
}
