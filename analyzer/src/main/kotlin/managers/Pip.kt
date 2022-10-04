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

import com.vdurmont.semver4j.Requirement

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
import org.ossreviewtoolkit.utils.common.ProcessCapture
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.ort.createOrtTempDir
import org.ossreviewtoolkit.utils.ort.createOrtTempFile
import org.ossreviewtoolkit.utils.ort.showStackTrace

object VirtualEnv : CommandLineTool {
    override fun command(workingDir: File?) = "virtualenv"

    override fun transformVersion(output: String) =
        // The version string can be something like:
        // 16.6.1
        // virtualenv 20.0.14 from /usr/local/lib/python2.7/dist-packages/virtualenv/__init__.pyc
        output.removePrefix("virtualenv ").substringBefore(' ')

    // Ensure a minimum version known to work. Note that virtualenv bundles a version of pip, and as of pip 20.3 a new
    // dependency resolver is used, see http://pyfound.blogspot.com/2020/11/pip-20-3-new-resolver.html.
    override fun getVersionRequirement(): Requirement = Requirement.buildIvy("[15.1,)")
}

object PythonVersion : CommandLineTool, Logging {
    // To use a specific version of Python on Windows we can use the "py" command with argument "-2" or "-3", see
    // https://docs.python.org/3/installing/#work-with-multiple-versions-of-python-installed-in-parallel.
    override fun command(workingDir: File?) = if (Os.isWindows) "py" else "python3"

    override fun transformVersion(output: String) = output.removePrefix("Python ")

    /**
     * Check all Python files in [workingDir] and return which version of Python they are compatible with. If all files
     * are compatible with Python 3, "3" is returned. If at least one file is incompatible with Python 3, "2" is
     * returned.
     */
    fun getPythonMajorVersion(workingDir: File): Int {
        val scriptFile = createOrtTempFile("python_compatibility", ".py")
        scriptFile.writeBytes(javaClass.getResource("/scripts/python_compatibility.py").readBytes())

        try {
            // The helper script itself always has to be run with Python 3.
            val scriptCmd = if (Os.isWindows) {
                run("-3", scriptFile.path, "-d", workingDir.path)
            } else {
                run(scriptFile.path, "-d", workingDir.path)
            }

            return scriptCmd.stdout.toInt()
        } finally {
            if (!scriptFile.delete()) {
                logger.warn { "Helper script file '$scriptFile' could not be deleted." }
            }
        }
    }

    /**
     * Return the absolute path to the Python interpreter for the given [version]. This is helpful as esp. on Windows
     * different Python versions can be installed in arbitrary locations, and the Python executable is even usually
     * called the same in those locations. Return `null` if no matching Python interpreter is available.
     */
    fun getPythonInterpreter(version: Int): String? =
        if (Os.isWindows) {
            val installedVersions = run("--list-paths").stdout
            val versionAndPath = installedVersions.lines().find { line ->
                line.startsWith(" -$version")
            }

            // Parse a line like " -2.7-32        C:\Python27\python.exe".
            versionAndPath?.split(' ', limit = 3)?.last()?.trimStart()
        } else {
            Os.getPathFromEnvironment("python$version")?.path
        }
}

private const val OPTION_OPERATING_SYSTEM = "operatingSystem"
private const val OPTION_OPERATING_SYSTEM_DEFAULT = "linux"
private val OPERATING_SYSTEMS = listOf("linux", "mac", "windows")

/**
 * The [PIP](https://pip.pypa.io/) package manager for Python. Also see
 * [install_requires vs requirements files](https://packaging.python.org/discussions/install-requires-vs-requirements/)
 * and [setup.py vs. requirements.txt](https://caremad.io/posts/2013/07/setup-vs-requirement/).
 *
 * This package manager supports the following [options][PackageManagerConfiguration.options]:
 * - *operatingSystem*: The name of the operating system to resolve dependencies for. One of "linux", "mac", or
 *   "windows". Defaults to "linux".
 */
@Suppress("TooManyFunctions")
class Pip(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analysisRoot, analyzerConfig, repoConfig), CommandLineTool {
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

    override fun command(workingDir: File?) = "pip"

    override fun transformVersion(output: String) = output.removePrefix("pip ").substringBefore(' ')

    private fun runInVirtualEnv(
        virtualEnvDir: File,
        workingDir: File,
        commandName: String,
        vararg commandArgs: String
    ): ProcessCapture {
        val binDir = if (Os.isWindows) "Scripts" else "bin"
        val command = virtualEnvDir.resolve(binDir).resolve(commandName)
        val resolvedCommand = Os.resolveWindowsExecutable(command)?.takeIf { Os.isWindows } ?: command

        // TODO: Maybe work around long shebang paths in generated scripts within a virtualenv by calling the Python
        //       executable in the virtualenv directly, see https://github.com/pypa/virtualenv/issues/997.
        val process = ProcessCapture(workingDir, resolvedCommand.path, *commandArgs)
        logger.debug { process.stdout }
        return process
    }

    override fun beforeResolution(definitionFiles: List<File>) = VirtualEnv.checkVersion()

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> {
        // For an overview, dependency resolution involves the following steps:
        // 1. Get metadata about the local project via `python setup.py`.
        // 2. Get the dependency tree and dependency metadata via python-inspector.

        val workingDir = definitionFile.parentFile

        // Try to determine the Python version the project requires.
        val pythonMajorVersion = PythonVersion.getPythonMajorVersion(workingDir)

        val virtualEnvDir = setupVirtualEnv(workingDir, pythonMajorVersion)

        val project = getProjectBasics(definitionFile, virtualEnvDir)
        val (packages, installDependencies) = getInstallDependencies(definitionFile, pythonMajorVersion)

        // TODO: Handle "extras" and "tests" dependencies.
        val scopes = sortedSetOf(
            Scope("install", installDependencies)
        )

        // Remove the virtualenv by simply deleting the directory.
        virtualEnvDir.safeDeleteRecursively()

        return listOf(ProjectAnalyzerResult(project.copy(scopeDependencies = scopes), packages))
    }

    private fun getProjectBasics(definitionFile: File, virtualEnvDir: File): Project {
        val authors = sortedSetOf<String>()
        val declaredLicenses = sortedSetOf<String>()

        val workingDir = definitionFile.parentFile

        // First try to get metadata from "setup.py" in any case, even for "requirements.txt" projects.
        val (setupName, setupVersion, setupHomepage) = if (workingDir.resolve("setup.py").isFile) {
            // See https://docs.python.org/3.8/distutils/setupscript.html#additional-meta-data.
            fun getSetupPyMetadata(option: String): String? {
                val process = runInVirtualEnv(virtualEnvDir, workingDir, "python", "setup.py", option)
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

    private fun getInstallDependencies(
        definitionFile: File,
        pythonMajorVersion: Int
    ): Pair<SortedSet<Package>, SortedSet<PackageReference>> {
        val workingDir = definitionFile.parentFile

        val pythonVersion = when (pythonMajorVersion) {
            2 -> "2.7" // 2.7 is the only 2.x version supported by python-inspector.
            3 -> "3.10" // 3.10 is the version currently used in the ORT Docker image.
            else -> throw IllegalArgumentException("Unsupported Python major version '$pythonMajorVersion'.")
        }

        logger.info {
            "Resolving dependencies for '${definitionFile.absolutePath}' with Python version '$pythonVersion' and " +
                    "operating system '$operatingSystemOption'."
        }

        val pythonInspectorResult = runCatching {
            try {
                PythonInspector.run(
                    workingDir = workingDir,
                    definitionFile = definitionFile,
                    pythonVersion = pythonVersion.replace(".", ""),
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

    private fun setupVirtualEnv(workingDir: File, pythonMajorVersion: Int): File {
        // Create an out-of-tree virtualenv.
        logger.info { "Creating a virtualenv for the '${workingDir.name}' project directory..." }

        val virtualEnvDir = createOrtTempDir("${workingDir.name}-virtualenv")
        val pythonInterpreter = requireNotNull(PythonVersion.getPythonInterpreter(pythonMajorVersion)) {
            "No Python interpreter found for version $pythonMajorVersion."
        }

        ProcessCapture(workingDir, "virtualenv", virtualEnvDir.path, "-p", pythonInterpreter).requireSuccess()

        return virtualEnvDir
    }
}
