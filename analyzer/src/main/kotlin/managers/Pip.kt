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

import com.here.ort.analyzer.AbstractPackageManagerFactory
import com.here.ort.analyzer.PackageManager
import com.here.ort.analyzer.managers.utils.PythonSupport
import com.here.ort.model.ProjectAnalyzerResult
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.utils.CommandLineTool
import com.here.ort.utils.Os
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.getPathFromEnvironment
import com.here.ort.utils.log
import com.here.ort.utils.safeDeleteRecursively

import com.vdurmont.semver4j.Requirement

import java.io.File
import java.io.IOException

const val PIP_VERSION = "19.1.1"
const val PIPDEPTREE_VERSION = "0.13.2"

object VirtualEnv : CommandLineTool {
    override fun command(workingDir: File?) = "virtualenv"

    // Allow to use versions that are known to work. Note that virtualenv bundles a version of pip.
    override fun getVersionRequirement(): Requirement = Requirement.buildIvy("[15.1,17.0[")
}

object PythonVersion : CommandLineTool {
    // To use a specific version of Python on Windows we can use the "py" command with argument "-2" or "-3", see
    // https://docs.python.org/3/installing/#work-with-multiple-versions-of-python-installed-in-parallel.
    override fun command(workingDir: File?) = if (Os.isWindows) "py" else "python3"

    /**
     * Check all Python files in [workingDir] and return which version of Python they are compatible with. If all files
     * are compatible with Python 3, "3" is returned. If at least one file is incompatible with Python 3, "2" is
     * returned.
     */
    fun getPythonVersion(workingDir: File): Int {
        val scriptFile = File.createTempFile("python_compatibility", ".py")
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
     * called the same in those locations.
     */
    fun getPythonInterpreter(version: Int): String =
        if (Os.isWindows) {
            val scriptFile = File.createTempFile("python_interpreter", ".py")
            scriptFile.writeBytes(javaClass.getResource("/scripts/python_interpreter.py").readBytes())

            try {
                run("-$version", scriptFile.path).stdout
            } finally {
                if (!scriptFile.delete()) {
                    log.warn { "Helper script file '${scriptFile.path}' could not be deleted." }
                }
            }
        } else {
            getPathFromEnvironment("python$version")?.path.orEmpty()
        }
}

/**
 * The [PIP](https://pip.pypa.io/) package manager for Python. Also see
 * [install_requires vs requirements files](https://packaging.python.org/discussions/install-requires-vs-requirements/)
 * and [setup.py vs. requirements.txt](https://caremad.io/posts/2013/07/setup-vs-requirement/).
 */
class Pip(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analysisRoot, analyzerConfig, repoConfig), CommandLineTool {
    class Factory : AbstractPackageManagerFactory<Pip>("PIP") {
        override val globsForDefinitionFiles = listOf("requirements*.txt", "setup.py")

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
        val TRUSTED_HOSTS = listOf(
            "pypi.org",
            "pypi.python.org" // Legacy
        ).flatMap { listOf("--trusted-host", it) }.toTypedArray()
    }

    override fun command(workingDir: File?) = "pip"

    private fun runPipInVirtualEnv(virtualEnvDir: File, workingDir: File, vararg commandArgs: String) =
        runInVirtualEnv(virtualEnvDir, workingDir, command(workingDir), *TRUSTED_HOSTS, *commandArgs)

    private fun runInVirtualEnv(virtualEnvDir: File, workingDir: File, commandName: String, vararg commandArgs: String):
            ProcessCapture {

        val binDir = if (Os.isWindows) "Scripts" else "bin"
        var command = File(virtualEnvDir, binDir + File.separator + commandName)
        if (Os.isWindows && command.extension.isEmpty()) {
            // On Windows specifying the extension is optional, so try them in order.
            val extensions = Os.env["PATHEXT"]?.splitToSequence(File.pathSeparatorChar).orEmpty()
            val commandWin = extensions.map { File(command.path + it.toLowerCase()) }.find { it.isFile }
            if (commandWin != null) {
                command = commandWin
            }
        }

        // TODO: Maybe work around long shebang paths in generated scripts within a virtualenv by calling the Python
        //       executable in the virtualenv directly, see https://github.com/pypa/virtualenv/issues/997.
        return ProcessCapture(workingDir, command.path, *commandArgs)
    }

    override fun beforeResolution(definitionFiles: List<File>) =
        VirtualEnv.checkVersion(ignoreActualVersion = analyzerConfig.ignoreToolVersions)

    @Suppress("LongMethod")
    override fun resolveDependencies(definitionFile: File): ProjectAnalyzerResult? {
        val workingDir = definitionFile.parentFile
        val virtualEnvDir = setupVirtualEnv(workingDir, definitionFile)

        val support = object : PythonSupport(managerName, analysisRoot, command(workingDir), virtualEnvDir) {
            override fun runInEnv(workingDir: File, commandName: String, vararg commandArgs: String): ProcessCapture =
                runInVirtualEnv(virtualEnvDir, workingDir, commandName, *commandArgs)
        }

        val result = support.resolveDependencies(definitionFile)

        // Remove the virtualenv by simply deleting the directory.
        virtualEnvDir.safeDeleteRecursively()

        return result
    }

    private fun setupVirtualEnv(workingDir: File, definitionFile: File): File {
        // Create an out-of-tree virtualenv.
        log.info { "Creating a virtualenv for the '${workingDir.name}' project directory..." }

        // Try to determine the Python version the project requires.
        var projectPythonVersion = PythonVersion.getPythonVersion(workingDir)

        log.info { "Trying to install dependencies using Python $projectPythonVersion..." }

        var virtualEnvDir = createVirtualEnv(workingDir, projectPythonVersion)
        var install = installDependencies(workingDir, definitionFile, virtualEnvDir)

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

            virtualEnvDir = createVirtualEnv(workingDir, projectPythonVersion)
            install = installDependencies(workingDir, definitionFile, virtualEnvDir)

            if (install.isError) {
                // pip writes the real error message to stdout instead of stderr.
                throw IOException(install.stdout)
            }
        }

        log.info {
            "Successfully installed dependencies for project '$definitionFile' using Python $projectPythonVersion."
        }

        return virtualEnvDir
    }

    private fun createVirtualEnv(workingDir: File, pythonVersion: Int): File {
        val virtualEnvDir = createTempDir("ort", "${workingDir.name}-virtualenv")

        val pythonInterpreter = PythonVersion.getPythonInterpreter(pythonVersion)
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
            if (isError) {
                log.error { errorMessage }
            }
        }

        return pip
    }
}
