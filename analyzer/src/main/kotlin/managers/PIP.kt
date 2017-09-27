package com.here.provenanceanalyzer.managers

import ch.frankel.slf4k.*

import com.fasterxml.jackson.databind.JsonNode

import com.here.provenanceanalyzer.Main
import com.here.provenanceanalyzer.PackageManager
import com.here.provenanceanalyzer.model.Package
import com.here.provenanceanalyzer.model.Project
import com.here.provenanceanalyzer.model.ScanResult
import com.here.provenanceanalyzer.util.OS
import com.here.provenanceanalyzer.util.ProcessCapture
import com.here.provenanceanalyzer.util.checkCommandVersion
import com.here.provenanceanalyzer.util.jsonMapper
import com.here.provenanceanalyzer.util.log

import com.vdurmont.semver4j.Semver

import java.io.File
import java.io.IOException

object PIP : PackageManager(
        "https://pip.pypa.io/",
        "Python",
        // See https://packaging.python.org/discussions/install-requires-vs-requirements/ and
        // https://caremad.io/posts/2013/07/setup-vs-requirement/.
        listOf("requirements*.txt", "setup.py")
) {
    private const val PIPDEPTREE_VERSION = "0.10.1"
    private const val PYDEP_REVISION = "ea18b40fca03438a0fb362e552c26df2d29fc19f"

    // TODO: Need to replace this hard-coded list of domains with e.g. a command line option.
    private val TRUSTED_HOSTS = listOf(
        "pypi.python.org"
    ).flatMap { listOf("--trusted-host", it) }.toTypedArray()

    override fun command(workingDir: File): String {
        return "pip"
    }

    private fun runPipInVirtualEnv(virtualEnvDir: File, workingDir: File, vararg commandArgs: String): ProcessCapture {
        return runInVirtualEnv(virtualEnvDir, workingDir, command(workingDir), *commandArgs)
    }

    private fun runInVirtualEnv(virtualEnvDir: File, workingDir: File, commandName: String, vararg commandArgs: String)
            : ProcessCapture {
        val binDir = if (OS.isWindows) "Scripts" else "bin"
        var command = File(virtualEnvDir, binDir + File.separator + commandName)

        if (OS.isWindows && command.extension.isEmpty()) {
            // On Windows specifying the extension is optional, so try them in order.
            val extensions = System.getenv("PATHEXT").split(File.pathSeparator)
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

    override fun resolveDependencies(projectDir: File, definitionFiles: List<File>): Map<File, ScanResult> {
        // virtualenv bundles pip. In order to get pip 9.0.1 inside a virtualenv, which is a version that supports
        // installing packages from a Git URL that include a commit SHA1, we need at least virtualenv 15.1.0.
        checkCommandVersion("virtualenv", Semver("15.1.0"), ignoreActualVersion = Main.ignoreVersions)

        val result = mutableMapOf<File, ScanResult>()

        definitionFiles.forEach { definitionFile ->
            val (virtualEnvDir, workingDir) = setupVirtualEnv(definitionFile)

            val packages = mutableListOf<Package>()

            // List all packages installed locally in the virtualenv in JSON format.
            val pipdeptree = runInVirtualEnv(virtualEnvDir, workingDir, "pipdeptree", "-l", "--json")

            // Install pydep after running pipdeptree but before looking at the dependencies because it downgrades
            // pip to version 7.1.2. Use it to get meta-information from about the project from setup.py. As pydep is
            // not on PyPI, install it from Git instead.
            val pip = runPipInVirtualEnv(virtualEnvDir, workingDir, "install",
                    "git+https://github.com/sourcegraph/pydep@$PYDEP_REVISION")
            with(pip) {
                if (exitValue() != 0) {
                    throw IOException("'$commandLine' failed with exit code ${exitValue()}:\n${stderr()}")
                }
            }

            val pydep = runInVirtualEnv(virtualEnvDir, workingDir, "pydep-run.py", "info", ".")
            with(pydep) {
                if (exitValue() != 0) {
                    throw IOException("'$commandLine' failed with exit code ${exitValue()}:\n${stderr()}")
                }
            }

            val (projectName, projectVersion, projectRepo) = jsonMapper.readTree(pydep.stdout()).let {
                listOf(it["project_name"].asText(), it["version"].asText(), it["repo_url"].asText())
            }

            if (pipdeptree.exitValue() == 0) {
                val dependencies = jsonMapper.readTree(pipdeptree.stdout())
                addPackagesForDependencies(projectName, dependencies, packages)
            } else {
                log.error { "Unable to determine dependencies for project in directory '$workingDir'." }
            }

            val project = Project(
                    packageManager = javaClass.simpleName,
                    namespace = "",
                    name = projectName,
                    aliases = emptyList(),
                    version = projectVersion,
                    vcsPath = null,
                    vcsProvider = "",
                    vcsUrl = projectRepo,
                    revision = "",
                    homepageUrl = "",
                    scopes = emptyList()
            )

            packages.sortBy { it.identifier }
            result[definitionFile] = ScanResult(project, packages)

            // Remove the virtualenv by simply deleting the directory.
            virtualEnvDir.deleteRecursively()
        }

        return result
    }

    private fun setupVirtualEnv(definitionFile: File): List<File> {
        val workingDir = definitionFile.parentFile

        // Create an out-of-tree virtualenv.
        println("Creating a virtualenv for the '${workingDir.name}' project directory...")
        val virtualEnvDir = createTempDir(workingDir.name, "virtualenv")
        val virtualEnv = ProcessCapture(workingDir, "virtualenv", virtualEnvDir.path)
        with(virtualEnv) {
            if (exitValue() != 0) {
                throw IOException("'$commandLine' failed with exit code ${exitValue()}:\n${stderr()}")
            }
        }

        var pip: ProcessCapture

        // Install pipdeptree inside the virtualenv as that's the only way to make it report only the project's
        // dependencies instead of those of all (globally) installed packages, see
        // https://github.com/naiquevin/pipdeptree#known-issues.
        // We only depend on pipdeptree to be at least version 0.5.0 for JSON output, but we stick to a fixed
        // version to be sure to get consistent results.
        pip = runPipInVirtualEnv(virtualEnvDir, workingDir, "install", "pipdeptree==$PIPDEPTREE_VERSION")
        with(pip) {
            if (exitValue() != 0) {
                throw IOException("'$commandLine' failed with exit code ${exitValue()}:\n${stderr()}")
            }
        }

        // TODO: Find a way to make installation of packages with native extensions work on Windows where often
        // the appropriate compiler is missing / not set up, e.g. by using pre-built packages from
        // http://www.lfd.uci.edu/~gohlke/pythonlibs/
        println("Installing dependencies for the '${workingDir.name}' project directory...")
        pip = if (definitionFile.name == "setup.py") {
            // TODO: Check what types of dependencies this covers: "install_requires", "extras_require",
            // "tests_require".
            runPipInVirtualEnv(virtualEnvDir, workingDir, *TRUSTED_HOSTS, "install", ".")
        } else {
            // In "setup.py"-speak, "requirements.txt" just contains "install_requires".
            runPipInVirtualEnv(virtualEnvDir, workingDir, *TRUSTED_HOSTS, "install", "-r", definitionFile.name)
        }

        // TODO: Consider logging a warning instead of an error if the command is run on a file that likely belongs
        // to a test.
        with(pip) {
            if (exitValue() != 0) {
                log.error { "'$commandLine' failed with exit code ${exitValue()}:\n${stderr()}" }
            }
        }

        return listOf(virtualEnvDir, workingDir)
    }

    private fun addPackagesForDependencies(rootPackageName: String, allDependencies: Iterable<JsonNode>,
                                           packages: MutableList<Package>) {
        // pipdeptree returns JSON like:
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
                    packageManager = javaClass.simpleName,
                    namespace = "",
                    name = packageName,
                    description = "",
                    version = packageVersion,
                    homepageUrl = null,
                    downloadUrl = null,
                    hash = "",
                    hashAlgorithm = null,
                    vcsPath = null,
                    vcsProvider = null,
                    vcsUrl = null,
                    vcsRevision = null
            )
            packages.add(dependencyPackage)

            addPackagesForDependencies(packageName, allDependencies, packages)
        }
    }
}
