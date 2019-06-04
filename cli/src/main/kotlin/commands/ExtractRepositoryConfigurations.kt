/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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

package com.here.ort.commands

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters

import com.here.ort.CommandWithHelp
import com.here.ort.analyzer.PackageManager
import com.here.ort.model.OrtResult
import com.here.ort.model.Repository
import com.here.ort.model.config.Excludes
import com.here.ort.model.config.PathExclude
import com.here.ort.model.config.RepositoryConfigurations
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.model.mapper
import com.here.ort.model.readValue

import com.here.ort.utils.PARAMETER_ORDER_MANDATORY
import java.io.File

@Parameters(commandNames = ["extract-repository-configurations"], commandDescription = "Extract repository configurations for the nested repositories.")
object ExtractRepositoryConfigurations : CommandWithHelp() {
    @Parameter(description = "The input ort result file.",
        names = ["--input-ort-result-file", "-i"],
        required = true,
        order = PARAMETER_ORDER_MANDATORY)
    private lateinit var inputOrtResultFile: File

    @Parameter(description = "The output yaml file.",
        names = ["--output-yml-file", "-o"],
        required = true,
        order = PARAMETER_ORDER_MANDATORY)
    private lateinit var outputFile: File

    override fun runCommand(jc: JCommander): Int {
        val ortResult: OrtResult = inputOrtResultFile.readValue()
        val configurations = getRepositoryConfigurations(ortResult)

        outputFile.mapper().writerWithDefaultPrettyPrinter().writeValue(outputFile, configurations)
        println("Wrote ${configurations.configurations.size} entries to ${outputFile.absolutePath}.")

        return 0
    }

    private fun getRepositoryConfigurations(ortResult: OrtResult, omitEmpty: Boolean = true)
            : RepositoryConfigurations {
        val pathExcludes = getPathExcludesByRepository(ortResult.repository)

        val configurations = mutableMapOf<String, RepositoryConfiguration>()
        pathExcludes.keys.forEach { vcsUrl ->
            configurations[vcsUrl] = RepositoryConfiguration(
                excludes = Excludes(
                    paths = pathExcludes[vcsUrl]!!.toList().sortedBy { it.pattern }
                )
            )
        }

        return RepositoryConfigurations(configurations = configurations.filter {
            !omitEmpty || it.value.excludes!!.paths.isNotEmpty()
        }.toSortedMap())
    }

    private fun getPathExcludesByRepository(repository: Repository): Map<String, Set<PathExclude>> {
        val result= mutableMapOf<String, MutableSet<PathExclude>>()

        val pathExcludes = repository.config.excludes?.paths ?: emptyList()
        repository.nestedRepositories.forEach { (path, vcs) ->
            val pathExcludesForRepository = result.getOrPut(vcs.url) { mutableSetOf() }
            pathExcludes.forEach { pathExclude ->
                if (pathExclude.pattern.startsWith(path) && !isDefinitionsFile(pathExclude)) {
                    pathExcludesForRepository.add(
                        pathExclude.copy(
                            pattern = pathExclude.pattern.substring(path.length).removePrefix("/")
                        )
                    )
                }
            }
        }

        return result
    }

    private fun isDefinitionsFile(pathExclude: PathExclude) = PackageManager.ALL.any {
        it.matchersForDefinitionFiles.any {
            pathExclude.pattern.endsWith(it.toString())
        }
    }
}
