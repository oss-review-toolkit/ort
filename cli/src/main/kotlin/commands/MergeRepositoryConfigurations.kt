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
import com.here.ort.model.config.Excludes
import com.here.ort.model.config.PathExclude
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.model.config.RepositoryConfigurations
import com.here.ort.model.mapper
import com.here.ort.model.readValue
import com.here.ort.utils.PARAMETER_ORDER_MANDATORY
import java.io.File

@Parameters(commandNames = ["merge-repository-configurations"], commandDescription = "Extract repository configurations for the nested repositories.")
object MergeRepositoryConfigurations : CommandWithHelp() {
    @Parameter(description = "The input repository configurations file.",
        names = ["--input-repo-configs-file", "-i"],
        required = true,
        order = PARAMETER_ORDER_MANDATORY)
    private lateinit var inputRepoConfigsFile: File

    @Parameter(description = "The output repository configurations file.",
        names = ["--output-repo-configs-file", "-o"],
        required = true,
        order = PARAMETER_ORDER_MANDATORY)
    private lateinit var outputRepoConfigsFile: File

    override fun runCommand(jc: JCommander): Int {
        val inputRepoConfigs: RepositoryConfigurations = inputRepoConfigsFile.readValue()
        val outputRepoConfigs: RepositoryConfigurations = outputRepoConfigsFile.readValue()

        val mergedConfigs = mutableMapOf<String, RepositoryConfiguration>()
        mergedConfigs.putAll(outputRepoConfigs.configurations)

        inputRepoConfigs.configurations.forEach { vcsUrl, config ->
            if (!mergedConfigs.containsKey(vcsUrl)) {
                mergedConfigs.put(vcsUrl, config)
            } else {
                mergedConfigs.put(vcsUrl,  merge(mergedConfigs[vcsUrl]!!, config))
            }
        }

        val result = RepositoryConfigurations(mergedConfigs.toSortedMap())
        outputRepoConfigsFile.mapper().writerWithDefaultPrettyPrinter().writeValue(
            outputRepoConfigsFile, result)

        return 0
    }

    private fun merge(base: RepositoryConfiguration, overlay: RepositoryConfiguration) =
        RepositoryConfiguration(
            excludes = Excludes(
                paths = merge(base.excludes?.paths?: emptyList(), overlay.excludes?.paths?: emptyList())
            )
        )

    private fun merge(base: Collection<PathExclude>, overlay: Collection<PathExclude>)
            :List<PathExclude> {
        val result = mutableMapOf<String, PathExclude>()

        base.forEach {
            result.put(it.pattern, it)
        }
        overlay.forEach {
            result.put(it.pattern, it)
        }

        return result.values.toList().sortedBy { it.pattern }
    }
}
