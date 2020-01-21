/*
 * Copyright (C) 2020 HERE Europe B.V.
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
import com.here.ort.helper.common.merge
import com.here.ort.helper.common.writeAsYaml
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.model.readValue
import com.here.ort.utils.PARAMETER_ORDER_MANDATORY
import com.here.ort.utils.expandTilde

import java.io.File

@Parameters(
    commandNames = ["merge-repository-configurations"],
    commandDescription = "Merges the given list of input repository configuration files and writes the result to " +
            "the given output repository configuration file."
)
internal class MergeRepositoryConfigurationsCommand : CommandWithHelp() {
    @Parameter(
        names = ["--input-repository-configuration-files", "-i"],
        required = true,
        order = PARAMETER_ORDER_MANDATORY,
        description = "A comma separated list of the repository configuration files to be merged."
    )
    private lateinit var inputRepositoryConfigurationFiles: List<File>

    @Parameter(
        names = ["--output-repository-configuration-file", "-o"],
        required = true,
        order = PARAMETER_ORDER_MANDATORY,
        description = "The output repository configuration file."
    )
    private lateinit var outputRepositoryConfigurationFile: File

    override fun runCommand(jc: JCommander): Int {
        var result = RepositoryConfiguration()

        inputRepositoryConfigurationFiles.forEach { file ->
            val repositoryConfiguration = file.expandTilde().readValue<RepositoryConfiguration>()
            result = result.merge(repositoryConfiguration)
        }

        result.writeAsYaml(outputRepositoryConfigurationFile)

        return 0
    }
}
