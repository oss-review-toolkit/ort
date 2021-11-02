/*
 * Copyright (C) 2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.helper.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.file

import java.io.File

import org.ossreviewtoolkit.helper.common.merge
import org.ossreviewtoolkit.helper.common.write
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.utils.common.expandTilde

internal class MergeRepositoryConfigurationsCommand : CliktCommand(
    help = "Merges the given list of input repository configuration files and writes the result to the given output " +
            "repository configuration file."
) {
    private val inputRepositoryConfigurationFiles by option(
        "--input-repository-configuration-files", "-i",
        help = "A comma separated list of the repository configuration files to be merged."
    ).convert { File(it.expandTilde()).absoluteFile.normalize() }.split(",").required()

    private val outputRepositoryConfigurationFile by option(
        "--output-repository-configuration-file", "-o",
        help = "The output repository configuration file."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()

    override fun run() {
        var result = RepositoryConfiguration()

        inputRepositoryConfigurationFiles.forEach { file ->
            val repositoryConfiguration = file.readValue<RepositoryConfiguration>()
            result = result.merge(repositoryConfiguration)
        }

        result.write(outputRepositoryConfigurationFile)
    }
}
