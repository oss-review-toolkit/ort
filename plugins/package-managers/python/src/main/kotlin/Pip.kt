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

package org.ossreviewtoolkit.plugins.packagemanagers.python

import java.io.File

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.PackageManagerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.plugins.packagemanagers.python.utils.PythonInspector
import org.ossreviewtoolkit.plugins.packagemanagers.python.utils.toOrtPackages
import org.ossreviewtoolkit.plugins.packagemanagers.python.utils.toOrtProject
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.ort.showStackTrace

private const val OPTION_OPERATING_SYSTEM_DEFAULT = "linux"
private val OPERATING_SYSTEMS = listOf(OPTION_OPERATING_SYSTEM_DEFAULT, "macos", "windows")

private const val OPTION_PYTHON_VERSION_DEFAULT = "3.11"
internal val PYTHON_VERSIONS = listOf("2.7", "3.6", "3.7", "3.8", "3.9", "3.10", OPTION_PYTHON_VERSION_DEFAULT)

private const val OPTION_ANALYZE_SETUP_PY_INSECURELY_DEFAULT = true

/**
 * The [PIP](https://pip.pypa.io/) package manager for Python. Also see
 * [install_requires vs requirements files](https://packaging.python.org/discussions/install-requires-vs-requirements/)
 * and [setup.py vs. requirements.txt](https://caremad.io/posts/2013/07/setup-vs-requirement/).
 *
 * This package manager supports the following [options][PackageManagerConfiguration.options]:
 * - *analyzeSetupPyInsecurely*: If "true", `python-inspector` resolves dependencies from setup.py files by executing
 *   them. This is a potential security risk. Defaults to [OPTION_ANALYZE_SETUP_PY_INSECURELY_DEFAULT].
 * - *operatingSystem*: The name of the operating system to resolve dependencies for. One of "linux", "macos", or
 *   "windows". Defaults to [OPTION_OPERATING_SYSTEM_DEFAULT].
 * - *pythonVersion*: The Python version to resolve dependencies for. Defaults to [OPTION_PYTHON_VERSION_DEFAULT].
 */
class Pip(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, "PIP", analysisRoot, analyzerConfig, repoConfig) {
    companion object {
        const val OPTION_OPERATING_SYSTEM = "operatingSystem"
        const val OPTION_PYTHON_VERSION = "pythonVersion"
        const val OPTION_ANALYZE_SETUP_PY_INSECURELY = "analyzeSetupPyInsecurely"
    }

    class Factory : AbstractPackageManagerFactory<Pip>("PIP") {
        override val globsForDefinitionFiles = listOf("*requirements*.txt", "setup.py")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Pip(type, analysisRoot, analyzerConfig, repoConfig)
    }

    private val operatingSystemOption = options[OPTION_OPERATING_SYSTEM]?.also { os ->
        require(os.isEmpty() || os in OPERATING_SYSTEMS) {
            val acceptedValues = OPERATING_SYSTEMS.joinToString { "'$it'" }
            "The '$OPTION_OPERATING_SYSTEM' option must be one of $acceptedValues, but was '$os'."
        }
    }

    private val pythonVersionOption = options[OPTION_PYTHON_VERSION]?.also { pythonVersion ->
        require(pythonVersion in PYTHON_VERSIONS) {
            val acceptedValues = PYTHON_VERSIONS.joinToString { "'$it'" }
            "The '$OPTION_PYTHON_VERSION' option must be one of $acceptedValues, but was '$pythonVersion'."
        }
    }

    private val analyzeSetupPyInsecurelyOption = options[OPTION_ANALYZE_SETUP_PY_INSECURELY]?.also { analyzeSetupPy ->
        requireNotNull(analyzeSetupPy.toBooleanStrictOrNull()) {
            "The '$OPTION_ANALYZE_SETUP_PY_INSECURELY' option must be 'true' or 'false', but was '$analyzeSetupPy'."
        }
    }

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>): List<ProjectAnalyzerResult> {
        val result = runPythonInspector(definitionFile) { detectPythonVersion(definitionFile.parentFile) }

        val project = result.toOrtProject(managerName, analysisRoot, definitionFile)
        val packages = result.packages.toOrtPackages()

        return listOf(ProjectAnalyzerResult(project, packages))
    }

    internal fun runPythonInspector(
        definitionFile: File,
        detectPythonVersion: () -> String? = { null }
    ): PythonInspector.Result {
        val operatingSystem = operatingSystemOption ?: OPTION_OPERATING_SYSTEM_DEFAULT
        val pythonVersion = pythonVersionOption ?: detectPythonVersion() ?: OPTION_PYTHON_VERSION_DEFAULT
        val analyzeSetupPyInsecurely = analyzeSetupPyInsecurelyOption?.toBooleanStrict()
            ?: OPTION_ANALYZE_SETUP_PY_INSECURELY_DEFAULT
        val workingDir = definitionFile.parentFile

        logger.info {
            "Resolving dependencies for '${definitionFile.absolutePath}' with Python version '$pythonVersion' " +
                "and operating system '$operatingSystem'."
        }

        return runCatching {
            try {
                PythonInspector.inspect(
                    workingDir = workingDir,
                    definitionFile = definitionFile,
                    pythonVersion = pythonVersion.split('.', limit = 3).take(2).joinToString(""),
                    operatingSystem = operatingSystem,
                    analyzeSetupPyInsecurely = analyzeSetupPyInsecurely
                )
            } finally {
                workingDir.resolve(".cache").safeDeleteRecursively()
            }
        }.onFailure { e ->
            e.showStackTrace()

            logger.error {
                "Unable to determine dependencies for definition file '${definitionFile.absolutePath}': " +
                    e.collectMessages()
            }
        }.getOrThrow()
    }

    private fun detectPythonVersion(workingDir: File): String? {
        // While there seems to be no formal specification, a `.python-version` file seems to be supposed to just
        // contain the plain version, see e.g. https://github.com/pyenv/pyenv/blob/21c2a3d/test/version.bats#L28.
        val pythonVersionFile = workingDir.resolve(".python-version")
        if (!pythonVersionFile.isFile) return null
        return pythonVersionFile.readLines().firstOrNull()?.takeIf { it.firstOrNull()?.isDigit() == true }
    }
}
