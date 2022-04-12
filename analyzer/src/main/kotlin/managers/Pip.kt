/*
 * Copyright (C) 2017-2021 HERE Europe B.V.
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

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode

import com.vdurmont.semver4j.Requirement

import java.io.File
import java.lang.IllegalArgumentException
import java.util.SortedSet

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.EMPTY_JSON_NODE
import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.ProcessCapture
import org.ossreviewtoolkit.utils.common.normalizeLineBreaks
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.common.textValueOrEmpty
import org.ossreviewtoolkit.utils.core.OkHttpClientHelper
import org.ossreviewtoolkit.utils.core.createOrtTempDir
import org.ossreviewtoolkit.utils.core.createOrtTempFile
import org.ossreviewtoolkit.utils.core.log

// Use the most recent version that still supports Python 2. PIP 21.0.0 dropped Python 2 support, see
// https://pip.pypa.io/en/stable/news/#id176.
private const val PIP_VERSION = "20.3.4"

// See https://github.com/naiquevin/pipdeptree.
private const val PIPDEPTREE_VERSION = "2.2.1"

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

object PythonVersion : CommandLineTool {
    // To use a specific version of Python on Windows we can use the "py" command with argument "-2" or "-3", see
    // https://docs.python.org/3/installing/#work-with-multiple-versions-of-python-installed-in-parallel.
    override fun command(workingDir: File?) = if (Os.isWindows) "py" else "python3"

    override fun transformVersion(output: String) = output.removePrefix("Python ")

    /**
     * Check all Python files in [workingDir] and return which version of Python they are compatible with. If all files
     * are compatible with Python 3, "3" is returned. If at least one file is incompatible with Python 3, "2" is
     * returned.
     */
    fun getPythonVersion(workingDir: File): Int {
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
                log.warn { "Helper script file '$scriptFile' could not be deleted." }
            }
        }
    }

    /**
     * Return the absolute path to the Python interpreter for the given [version]. This is helpful as esp. on Windows
     * different Python versions can by installed in arbitrary locations, and the Python executable is even usually
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

/**
 * The [PIP](https://pip.pypa.io/) package manager for Python. Also see
 * [install_requires vs requirements files](https://packaging.python.org/discussions/install-requires-vs-requirements/)
 * and [setup.py vs. requirements.txt](https://caremad.io/posts/2013/07/setup-vs-requirement/).
 */
@Suppress("TooManyFunctions")
class Pip(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analysisRoot, analyzerConfig, repoConfig), CommandLineTool {
    class Factory : AbstractPackageManagerFactory<Pip>("PIP") {
        override val globsForDefinitionFiles = listOf("*requirements*.txt", "setup.py")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Pip(managerName, analysisRoot, analyzerConfig, repoConfig)
    }

    companion object {
        private val INSTALL_OPTIONS = arrayOf(
            "--no-warn-conflicts",
            "--prefer-binary"
        )

        // TODO: Need to replace this hard-coded list of domains with e.g. a command line option.
        private val TRUSTED_HOSTS = listOf(
            "pypi.org",
            "pypi.python.org" // Legacy
        ).flatMap { listOf("--trusted-host", it) }.toTypedArray()

        /**
         * Return a version string with leading zeros of components stripped.
         */
        private fun stripLeadingZerosFromVersion(version: String) =
            version.split('.').joinToString(".") { it.trimStart('0').ifEmpty { "0" } }
    }

    override fun command(workingDir: File?) = "pip"

    override fun transformVersion(output: String) = output.removePrefix("pip ").substringBefore(' ')

    private fun runPipInVirtualEnv(virtualEnvDir: File, workingDir: File, vararg commandArgs: String) =
        runInVirtualEnv(virtualEnvDir, workingDir, command(workingDir), *TRUSTED_HOSTS, *commandArgs)

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
        log.debug { process.stdout }
        return process
    }

    override fun beforeResolution(definitionFiles: List<File>) = VirtualEnv.checkVersion()

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> {
        // For an overview, dependency resolution involves the following steps:
        // 1. Install dependencies via pip (inside a virtualenv, for isolation from globally installed packages).
        // 2. Get metadata about the local project via `python setup.py`.
        // 3. Get the hierarchy of dependencies via pipdeptree.
        // 4. Get additional remote package metadata via PyPIJSON.

        val workingDir = definitionFile.parentFile
        val virtualEnvDir = setupVirtualEnv(workingDir, definitionFile)

        val project = getProjectBasics(definitionFile, virtualEnvDir)
        val (packages, installDependencies) = getInstallDependencies(definitionFile, virtualEnvDir, project.id.name)

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
                log.debug {
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
        definitionFile: File, virtualEnvDir: File, projectName: String
    ): Pair<SortedSet<Package>, SortedSet<PackageReference>> {
        val packages = sortedSetOf<Package>()
        val installDependencies = sortedSetOf<PackageReference>()

        val workingDir = definitionFile.parentFile

        // List all packages installed locally in the virtualenv.
        val pipdeptree = runInVirtualEnv(virtualEnvDir, workingDir, "pipdeptree", "-l", "--json-tree")

        // Get the locally available metadata for all installed packages as a fallback.
        val installedPackages = getInstalledPackagesWithLocalMetaData(virtualEnvDir, workingDir).associateBy { it.id }

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

            // Enrich the package templates with additional metadata from PyPI.
            packageTemplates.mapTo(packages) { pkg ->
                // TODO: Retrieve metadata of package not hosted on PyPI by querying the respective repository.
                pkg.enrichWith(getPackageFromPyPi(pkg.id))
                    .enrichWith(installedPackages[pkg.id])
            }
        } else {
            log.error {
                "Unable to determine dependencies for project in directory '$workingDir':\n${pipdeptree.stderr}"
            }
        }

        return packages to installDependencies
    }

    private fun getBinaryArtifact(releaseNode: ArrayNode?): RemoteArtifact {
        releaseNode ?: return RemoteArtifact.EMPTY

        // Prefer python wheels and fall back to the first entry (probably a sdist).
        val binaryArtifact = releaseNode.find {
            it["packagetype"].textValue() == "bdist_wheel"
        } ?: releaseNode[0]

        val url = binaryArtifact["url"]?.textValue() ?: return RemoteArtifact.EMPTY
        val hash = binaryArtifact["md5_digest"]?.textValue()?.let { Hash.create(it) } ?: return RemoteArtifact.EMPTY

        return RemoteArtifact(url, hash)
    }

    private fun getSourceArtifact(releaseNode: ArrayNode?): RemoteArtifact {
        releaseNode ?: return RemoteArtifact.EMPTY

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

    private fun parseAuthors(pkgInfo: JsonNode): SortedSet<String> =
        parseAuthorString(pkgInfo["author"]?.textValue())

    private fun parseAuthorString(author: String?): SortedSet<String> =
        author?.takeIf(::isValidAuthor)?.let { sortedSetOf(it) } ?: sortedSetOf()

    /**
     * Check if the given [author] string represents a valid author name. There are some non-null strings that
     * indicate that no author information is available. For instance, setup.py files can contain empty strings;
     * the "pip show" command prints the string "None" in this case.
     */
    private fun isValidAuthor(author: String): Boolean = author.isNotBlank() && author != "None"

    private fun getDeclaredLicenses(pkgInfo: JsonNode): SortedSet<String> {
        val declaredLicenses = sortedSetOf<String>()

        // Use the top-level license field as well as the license classifiers as the declared licenses.
        getLicenseFromLicenseField(pkgInfo["license"]?.textValue())?.let { declaredLicenses += it }
        pkgInfo["classifiers"]?.mapNotNullTo(declaredLicenses) { getLicenseFromClassifier(it.textValue()) }

        return declaredLicenses
    }

    private fun getLicenseFromLicenseField(value: String?): String? =
        value?.let {
            // Work-around for projects that declare licenses in classifier-style syntax.
            getLicenseFromClassifier(it) ?: it
        }?.takeUnless {
            it.isBlank() || it == "UNKNOWN"
        }

    private fun getLicenseFromClassifier(classifier: String): String? {
        // Example license classifier:
        // "License :: OSI Approved :: GNU Library or Lesser General Public License (LGPL)"
        val classifiers = classifier.split(" :: ").map { it.trim() }
        val licenseClassifiers = listOf("License", "OSI Approved")
        val license = classifiers.takeIf { it.first() in licenseClassifiers }?.last()
        return license?.takeUnless { it in licenseClassifiers }
    }

    private fun setupVirtualEnv(workingDir: File, definitionFile: File): File {
        // Create an out-of-tree virtualenv.
        log.info { "Creating a virtualenv for the '${workingDir.name}' project directory..." }

        // Try to determine the Python version the project requires.
        var projectPythonVersion = PythonVersion.getPythonVersion(workingDir)

        log.info { "Trying to install dependencies using Python $projectPythonVersion..." }

        var virtualEnvDir = createVirtualEnv(workingDir, projectPythonVersion)
        val install = installDependencies(workingDir, definitionFile, virtualEnvDir)

        if (install.isError) {
            log.debug {
                // pip writes the real error message to stdout instead of stderr.
                "First try to install dependencies using Python $projectPythonVersion failed with:\n${install.stdout}"
            }

            // If there was a problem maybe the required Python version was detected incorrectly, so simply try again
            // with the other version.
            projectPythonVersion = when (projectPythonVersion) {
                2 -> 3
                3 -> 2
                else -> throw IllegalArgumentException("Unsupported Python version $projectPythonVersion.")
            }

            log.info { "Falling back to trying to install dependencies using Python $projectPythonVersion..." }

            virtualEnvDir.safeDeleteRecursively()
            virtualEnvDir = createVirtualEnv(workingDir, projectPythonVersion)
            installDependencies(workingDir, definitionFile, virtualEnvDir).requireSuccess()
        }

        log.info {
            "Successfully installed dependencies for project '$definitionFile' using Python $projectPythonVersion."
        }

        return virtualEnvDir
    }

    private fun createVirtualEnv(workingDir: File, pythonVersion: Int): File {
        val virtualEnvDir = createOrtTempDir("${workingDir.name}-virtualenv")
        val pythonInterpreter = requireNotNull(PythonVersion.getPythonInterpreter(pythonVersion)) {
            "No Python interpreter found for version $pythonVersion."
        }

        ProcessCapture(workingDir, "virtualenv", virtualEnvDir.path, "-p", pythonInterpreter).requireSuccess()

        return virtualEnvDir
    }

    private fun installDependencies(workingDir: File, definitionFile: File, virtualEnvDir: File): ProcessCapture {
        // Ensure to have installed a version of pip that is know to work for us.
        var pip = if (Os.isWindows) {
            // On Windows, in-place pip up- / downgrades require pip to be wrapped by "python -m", see
            // https://github.com/pypa/pip/issues/1299.
            runInVirtualEnv(
                virtualEnvDir, workingDir, "python", "-m", command(workingDir),
                *TRUSTED_HOSTS, "install", "pip==$PIP_VERSION"
            )
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

        // TODO: Find a way to make installation of packages with native extensions work on Windows where often the
        //       appropriate compiler is missing / not set up, e.g. by using pre-built packages from
        //       http://www.lfd.uci.edu/~gohlke/pythonlibs/
        pip = if (definitionFile.name == "setup.py") {
            // Note that this only installs required "install" dependencies, not "extras" or "tests" dependencies.
            runPipInVirtualEnv(virtualEnvDir, workingDir, "install", *INSTALL_OPTIONS, ".")
        } else {
            // In "setup.py"-speak, "requirements.txt" just contains required "install" dependencies.
            runPipInVirtualEnv(
                virtualEnvDir, workingDir, "install", *INSTALL_OPTIONS, "-r",
                definitionFile.name
            )
        }

        // TODO: Consider logging a warning instead of an error if the command is run on a file that likely belongs to
        //       a test.
        with(pip) {
            if (isError) log.error { errorMessage }
        }

        return pip
    }

    private fun parseDependencies(
        dependencies: Iterable<JsonNode>,
        allPackages: SortedSet<Package>,
        installDependencies: SortedSet<PackageReference>
    ) {
        dependencies.forEach { dependency ->
            val pkg = Package(
                id = Identifier(
                    type = "PyPI",
                    namespace = "",
                    name = dependency["package_name"].textValue().normalizePackageName(),
                    version = dependency["installed_version"].textValue()
                ),
                authors = sortedSetOf(),
                declaredLicenses = sortedSetOf(),
                description = "",
                homepageUrl = "",
                binaryArtifact = RemoteArtifact.EMPTY,
                sourceArtifact = RemoteArtifact.EMPTY,
                vcs = VcsInfo.EMPTY
            )
            val packageRef = pkg.toReference()

            allPackages += pkg
            installDependencies += packageRef

            parseDependencies(dependency["dependencies"], allPackages, packageRef.dependencies)
        }
    }

    private fun getPackageFromPyPi(id: Identifier): Package {
        // See https://wiki.python.org/moin/PyPIJSON.
        val url = "https://pypi.org/pypi/${id.name}/${id.version}/json"

        return OkHttpClientHelper.downloadText(url).mapCatching {
            val pkgData = jsonMapper.readTree(it)

            val pkgInfo = pkgData["info"]

            val pkgRelease = pkgData["releases"]?.let { pkgReleases ->
                val pkgVersion = pkgReleases.fieldNames().asSequence().find { version ->
                    stripLeadingZerosFromVersion(version) == id.version
                }

                pkgReleases[pkgVersion]
            } as? ArrayNode

            val homepageUrl = pkgInfo["home_page"]?.textValue().orEmpty()

            Package(
                id = id,
                homepageUrl = homepageUrl,
                description = pkgInfo["summary"]?.textValue().orEmpty(),
                authors = parseAuthors(pkgInfo),
                declaredLicenses = getDeclaredLicenses(pkgInfo),
                binaryArtifact = getBinaryArtifact(pkgRelease),
                sourceArtifact = getSourceArtifact(pkgRelease),
                vcs = VcsInfo.EMPTY,
                vcsProcessed = processPackageVcs(VcsInfo.EMPTY, homepageUrl)
            )
        }.onFailure {
            log.warn { "Unable to retrieve PyPI metadata for package '${id.toCoordinates()}'." }
        }.getOrDefault(Package.EMPTY.copy(id = id))
    }

    private fun getInstalledPackagesWithLocalMetaData(
        virtualEnvDir: File,
        workingDir: File
    ): List<Package> {
        val allPackages = listAllInstalledPackages(virtualEnvDir, workingDir)

        // Invoking 'pip show' once for each package separately is too slow, thus obtain the output for all packages
        // and split it at the separator lines: "---".
        val output = runInVirtualEnv(
            virtualEnvDir,
            workingDir,
            "pip",
            "show",
            "--verbose",
            *allPackages.map { it.name }.toTypedArray()
        ).requireSuccess().stdout

        return output.normalizeLineBreaks().split("---\n").map { parsePipShowOutput(it) }
    }

    /**
     * Return the [Identifier]s of all installed packages, determined via the command 'pip list'.
     */
    private fun listAllInstalledPackages(virtualEnvDir: File, workingDir: File): Set<Identifier> {
        val json = runInVirtualEnv(virtualEnvDir, workingDir, "pip", "list", "--format", "json")
            .requireSuccess()
            .stdout

        val rootNode = jsonMapper.readTree(json) as ArrayNode

        return rootNode.elements().asSequence().mapTo(mutableSetOf()) {
            Identifier("PyPI", "", it["name"].textValue(), it["version"].textValue())
        }
    }

    /**
     * Parse the output of 'pip show <package-name> --verbose' to a package.
     */
    private fun parsePipShowOutput(output: String): Package {
        val map = mutableMapOf<String, MutableList<String>>()

        var previousKey: String? = null
        output.lines().forEach { line ->
            if (!line.startsWith(" ")) {
                val index = line.indexOf(":")
                if (index < 0) return@forEach

                val key = line.substring(0, index)
                val value = line.substring(index + 1, line.length).trim()

                if (value.isNotEmpty()) {
                    map.getOrPut(key) { mutableListOf() } += value
                }

                previousKey = key
                return@forEach
            }

            previousKey?.let {
                map.getOrPut(it) { mutableListOf() } += line.trim()
            }
        }

        val id = Identifier(
            type = "PyPI",
            namespace = "",
            name = map.getValue("Name").single().normalizePackageName(),
            version = map.getValue("Version").single()
        )

        val declaredLicenses = sortedSetOf<String>()

        map["License"]?.let { licenseShortString ->
            getLicenseFromLicenseField(licenseShortString.firstOrNull())?.let { declaredLicenses += it }

            val moreLines = licenseShortString.drop(1)
            if (moreLines.isNotEmpty()) {
                log.warn {
                    "The 'License' field of package '${id.toCoordinates()}' is supposed to be a short string but it " +
                            "contains the following additional lines which will be ignored:"
                }

                moreLines.forEach { line ->
                    log.warn { line }
                }
            }
        }

        map["Classifiers"]?.mapNotNullTo(declaredLicenses) { getLicenseFromClassifier(it) }

        val authors = parseAuthorString(map["Author"]?.singleOrNull())

        return Package(
            id = id,
            description = map["Summary"]?.single().orEmpty(),
            homepageUrl = map["Home-page"]?.single().orEmpty(),
            authors = authors,
            declaredLicenses = declaredLicenses,
            binaryArtifact = RemoteArtifact.EMPTY,
            sourceArtifact = RemoteArtifact.EMPTY,
            vcs = VcsInfo.EMPTY
        )
    }
}

private fun Package.enrichWith(other: Package?): Package =
    if (other != null) {
        Package(
            id = id,
            homepageUrl = homepageUrl.takeUnless { it.isBlank() } ?: other.homepageUrl,
            description = description.takeUnless { it.isBlank() } ?: other.description,
            authors = authors.takeUnless { it.isEmpty() } ?: other.authors,
            declaredLicenses = declaredLicenses.takeUnless { it.isEmpty() } ?: other.declaredLicenses,
            binaryArtifact = binaryArtifact.takeUnless { it == RemoteArtifact.EMPTY } ?: other.binaryArtifact,
            sourceArtifact = sourceArtifact.takeUnless { it == RemoteArtifact.EMPTY } ?: other.sourceArtifact,
            vcs = vcs.takeUnless { it == VcsInfo.EMPTY } ?: other.vcs,
            vcsProcessed = vcsProcessed.takeUnless { it == VcsInfo.EMPTY } ?: other.vcsProcessed
        )
    } else {
        this
    }

/**
 * Normalize all PyPI package names to be lowercase and hyphenated as per PEP 426 and 503:
 *
 * PEP 426 (https://www.python.org/dev/peps/pep-0426/#name):
 * "All comparisons of distribution names MUST be case insensitive,
 * and MUST consider hyphens and underscores to be equivalent".
 *
 * PEP 503 (https://www.python.org/dev/peps/pep-0503/#normalized-names):
 * "This PEP references the concept of a "normalized" project name.
 * As per PEP 426 the only valid characters in a name are the ASCII alphabet,
 * ASCII numbers, ., -, and _. The name should be lowercased with all runs
 * of the characters ., -, or _ replaced with a single - character."
 */
private fun String.normalizePackageName(): String = replace(Regex("[-_.]+"), "-").lowercase()
