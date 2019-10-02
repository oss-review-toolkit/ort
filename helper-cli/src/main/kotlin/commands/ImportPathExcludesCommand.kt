/*
 * Copyright (C) 2019 HERE Europe B.V.
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

package com.here.ort.helper.commands

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters

import com.here.ort.helper.CommandWithHelp
import com.here.ort.helper.common.RepositoryPathExcludes
import com.here.ort.helper.common.findFilesRecursive
import com.here.ort.helper.common.findRepositoryPaths
import com.here.ort.helper.common.mergePathExcludes
import com.here.ort.helper.common.replacePathExcludes
import com.here.ort.helper.common.sortPathExcludes
import com.here.ort.helper.common.writeAsYaml
import com.here.ort.model.config.PathExclude
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.model.readValue
import com.here.ort.utils.PARAMETER_ORDER_MANDATORY
import com.here.ort.utils.PARAMETER_ORDER_OPTIONAL

import java.io.File

@Parameters(
    commandNames = ["import-path-excludes"],
    commandDescription = "Import path excludes by repository from a file into the given repository configuration."
)
internal class ImportPathExcludesCommand : CommandWithHelp() {
    @Parameter(
        names = ["--path-excludes-file"],
        required = true,
        order = PARAMETER_ORDER_MANDATORY
    )
    private lateinit var pathExcludesFile: File

    @Parameter(
        names = ["--source-code-dir"],
        required = true,
        order = PARAMETER_ORDER_MANDATORY
    )
    private lateinit var sourceCodeDir: File

    @Parameter(
        names = ["--repository-configuration-file"],
        required = true,
        order = PARAMETER_ORDER_MANDATORY
    )
    private lateinit var repositoryConfigurationFile: File

    @Parameter(
        names = ["--update-only-existing"],
        required = false,
        order = PARAMETER_ORDER_OPTIONAL
    )
    private var updateOnlyExisting = false

    override fun runCommand(jc: JCommander): Int {
        val allFiles = findFilesRecursive(sourceCodeDir)

        val repositoryConfiguration = if (repositoryConfigurationFile.isFile) {
            repositoryConfigurationFile.readValue()
        } else {
            RepositoryConfiguration()
        }

        val existingPathExcludes = repositoryConfiguration.excludes?.paths ?: emptyList()
        val importedPathExcludes = importPathExcludes().filter { pathExclude ->
            allFiles.any { pathExclude.matches(it) }
        }

        val pathExcludes = existingPathExcludes.mergePathExcludes(importedPathExcludes, updateOnlyExisting)

        repositoryConfiguration
            .replacePathExcludes(pathExcludes)
            .sortPathExcludes()
            .writeAsYaml(repositoryConfigurationFile)

        return 0
    }

    private fun importPathExcludes(): List<PathExclude> {
        println("Analyzing $sourceCodeDir...")
        val repositoryPaths = findRepositoryPaths(sourceCodeDir)
        println("Found ${repositoryPaths.size} repositories in ${repositoryPaths.values.sumBy { it.size }} locations.")

        println("Loading $pathExcludesFile...")
        val pathExcludes = pathExcludesFile.readValue<RepositoryPathExcludes>()
        println("Found ${pathExcludes.values.sumBy { it.size }} excludes for ${pathExcludes.size} repositories.")

        val result = mutableListOf<PathExclude>()

        repositoryPaths.forEach { vcsUrl, relativePaths ->
            pathExcludes[vcsUrl]?.let { pathExcludesForRepository ->
                pathExcludesForRepository.forEach { pathExclude ->
                    relativePaths.forEach { path ->
                        result.add(pathExclude.copy(pattern = path + '/' + pathExclude.pattern))
                    }
                }
            }
        }

        return result
    }
}
