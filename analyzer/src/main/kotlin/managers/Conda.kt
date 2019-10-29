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
import com.here.ort.model.jsonMapper
import com.here.ort.utils.CommandLineTool
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.log
import com.here.ort.utils.safeDeleteRecursively

import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * The [Conda](https://docs.conda.io/en/latest/) package manager for Python.
 */
class Conda(
    name: String,
    analyzerRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analyzerRoot, analyzerConfig, repoConfig), CommandLineTool {
    class Factory : AbstractPackageManagerFactory<Conda>("Conda") {
        override val globsForDefinitionFiles = listOf("environment.yml")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Conda(managerName, analysisRoot, analyzerConfig, repoConfig)
    }

    // Create an environment with Conda named "ort<datetime>".
    private val envName = run {
        val formatter = DateTimeFormatter.ofPattern("yyyyMMddhhmmss")
        val formattedDate = LocalDateTime.now().format(formatter)
        "ort$formattedDate"
    }

    override fun command(workingDir: File?) = "conda"

    /**
     * Run [commandName] with arguments [commandArgs] in environment at directory [envDir].
     */
    fun runInEnv(envDir: File, commandName: String, vararg commandArgs: String): ProcessCapture =
        ProcessCapture(command(), "run", "--name", envDir.name, commandName, *commandArgs).requireSuccess()

    override fun resolveDependencies(definitionFile: File): ProjectAnalyzerResult? {
        val workingDir = definitionFile.parentFile
        val envDir = setupEnv(definitionFile)

        val support = object : PythonSupport(managerName, analysisRoot, command(workingDir), envDir) {
            override fun runInEnv(workingDir: File, commandName: String, vararg commandArgs: String): ProcessCapture =
                this@Conda.runInEnv(envDir, commandName, *commandArgs)
        }

        val result = support.resolveDependencies(definitionFile)

        // Remove the env by simply deleting the directory.
        envDir.safeDeleteRecursively()

        return result
    }

    private fun setupEnv(definitionFile: File): File {
        // Create an out-of-tree environment.
        log.info { "Creating a Conda env for $definitionFile..." }
        val envDir = createEnv(definitionFile)
        val install = installDependencies(definitionFile, envDir)

        if (install.isError) {
            log.debug {
                // Pip writes the real error message to stdout instead of stderr.
                "First try to install dependencies using Conda failed with:\n${install.stdout}"
            }

            if (install.isError) {
                // pip writes the real error message to stdout instead of stderr.
                throw IOException("setupEnv (in $envDir) error: ${install.stdout}")
            }
        }

        log.info {
            "Successfully installed dependencies for project '$definitionFile' using Conda."
        }

        return envDir
    }

    /**
     * Create a Conda environment given the environment.yml or requirements.txt.
     */
    fun createEnv(envDefinition: File): File {
        if (envDefinition.name.endsWith(".py")) {
            // Python install script is not an environment definition.
            ProcessCapture(
                "conda", "create",
                "-y",
                "--name", envName,
                "python"
            ).requireSuccess()
        } else {
            ProcessCapture(
                "conda", "env", "create",
                "--force",
                "--name", envName,
                "--file", envDefinition.absolutePath
            ).requireSuccess()
        }

        // Load the list of Conda environments and find the one we created.
        val jsonString: String = ProcessCapture("conda", "env", "list", "--json").requireSuccess().stdout
        val rootNode = jsonMapper.readTree(jsonString)
        val envsNodes = rootNode.path("envs").elements().asSequence().toList()
        return File(envsNodes.single { File(it.textValue()).name == envName }.textValue())
    }

    private fun installDependencies(definitionFile: File, envDir: File): ProcessCapture {
        // Install pipdeptree inside the Conda environment as that is the only way to make it report only the project's
        // dependencies instead of those of all (globally) installed packages, see
        // https://github.com/naiquevin/pipdeptree#known-issues.
        // We only depend on pipdeptree to be at least version 0.5.0 for JSON output, but we stick to a fixed
        // version to be sure to get consistent results.
        runInEnv(envDir, "pip", *Pip.TRUSTED_HOSTS, "install", "pipdeptree==$PIPDEPTREE_VERSION").requireSuccess()

        // Use the yml that defines the environment.
        val conda = ProcessCapture(
            "conda",
            "env",
            "update",
            "--name", envName,
            "--file", definitionFile.absolutePath
        ).requireSuccess()

        return conda
    }
}
