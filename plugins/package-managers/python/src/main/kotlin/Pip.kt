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

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.packagemanagers.python.utils.DEFAULT_PYTHON_VERSION
import org.ossreviewtoolkit.plugins.packagemanagers.python.utils.PythonInspector
import org.ossreviewtoolkit.plugins.packagemanagers.python.utils.toOrtPackages
import org.ossreviewtoolkit.plugins.packagemanagers.python.utils.toOrtProject
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.ort.showStackTrace

private val OPERATING_SYSTEMS = listOf("linux", "macos", "windows")

data class PipConfig(
    /**
     * If "true", `python-inspector` resolves dependencies from setup.py files by executing them. This is a potential
     * security risk.
     */
    @OrtPluginOption(defaultValue = "true")
    val analyzeSetupPyInsecurely: Boolean,

    /**
     * The name of the operating system to resolve dependencies for. One of "linux", "macos", or "windows".
     */
    @OrtPluginOption(defaultValue = "linux")
    val operatingSystem: String,

    /**
     * The Python version to resolve dependencies for. If not set, the version is detected from the environment and if
     * that fails, the default version is used.
     */
    val pythonVersion: String?
)

/**
 * The [PIP](https://pip.pypa.io/) package manager for Python. Also see
 * [install_requires vs requirements files](https://packaging.python.org/discussions/install-requires-vs-requirements/)
 * and [setup.py vs. requirements.txt](https://caremad.io/posts/2013/07/setup-vs-requirement/).
 */
@OrtPlugin(
    id = "PIP",
    displayName = "PIP",
    description = "The PIP package manager for Python.",
    factory = PackageManagerFactory::class
)
class Pip internal constructor(
    override val descriptor: PluginDescriptor = PipFactory.descriptor,
    private val config: PipConfig,
    projectType: String
) : PackageManager(projectType) {
    constructor(descriptor: PluginDescriptor = PipFactory.descriptor, config: PipConfig) :
        this(descriptor, config, "PIP")

    override val globsForDefinitionFiles = listOf("*requirements*.txt", "setup.py")

    init {
        require(config.operatingSystem in OPERATING_SYSTEMS) {
            val acceptedValues = OPERATING_SYSTEMS.joinToString { "'$it'" }
            "The 'operatingSystem' option must be one of $acceptedValues, but was '${config.operatingSystem}'."
        }
    }

    override fun resolveDependencies(
        analysisRoot: File,
        definitionFile: File,
        excludes: Excludes,
        analyzerConfig: AnalyzerConfiguration,
        labels: Map<String, String>
    ): List<ProjectAnalyzerResult> {
        val result = runPythonInspector(definitionFile) { detectPythonVersion(definitionFile.parentFile) }

        val project = result.toOrtProject(projectType, analysisRoot, definitionFile)
        val packages = result.packages.toOrtPackages()

        return listOf(ProjectAnalyzerResult(project, packages))
    }

    internal fun runPythonInspector(
        definitionFile: File,
        detectPythonVersion: () -> String? = { null }
    ): PythonInspector.Result {
        val pythonVersion = config.pythonVersion ?: detectPythonVersion() ?: DEFAULT_PYTHON_VERSION
        val workingDir = definitionFile.parentFile

        logger.info {
            "Resolving dependencies for '${definitionFile.absolutePath}' with Python version '$pythonVersion' and " +
                "operating system '${config.operatingSystem}'."
        }

        return runCatching {
            try {
                PythonInspector.inspect(
                    workingDir = workingDir,
                    definitionFile = definitionFile,
                    pythonVersion = pythonVersion.split('.', limit = 3).take(2).joinToString(""),
                    operatingSystem = config.operatingSystem,
                    analyzeSetupPyInsecurely = config.analyzeSetupPyInsecurely
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
        val pythonVersionFile = workingDir / ".python-version"
        if (!pythonVersionFile.isFile) return null
        return pythonVersionFile.readLines().firstOrNull()?.takeIf { it.firstOrNull()?.isDigit() == true }
    }
}
