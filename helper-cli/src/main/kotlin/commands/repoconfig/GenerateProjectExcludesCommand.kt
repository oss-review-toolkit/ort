/*
 * Copyright (C) 2019 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.helper.commands.repoconfig

import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.helper.utils.OrtHelperCommand
import org.ossreviewtoolkit.helper.utils.readOrtResult
import org.ossreviewtoolkit.helper.utils.replacePathExcludes
import org.ossreviewtoolkit.helper.utils.sortPathExcludes
import org.ossreviewtoolkit.helper.utils.write
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.config.PathExcludeReason
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.utils.common.expandTilde

internal class GenerateProjectExcludesCommand : OrtHelperCommand(
    help = "Generates path excludes for all definition files which are not yet excluded. The output is written to " +
        "the given repository configuration file."
) {
    private val ortFile by option(
        "--ort-file", "-i",
        help = "The input ORT file from which the rule violations are read."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val repositoryConfigurationFile by option(
        "--repository-configuration-file",
        help = "The repository configuration file to write the result to. If the file does already exist it " +
            "overrides the repository configuration contained in the given input ORT file."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()

    override fun run() {
        val repositoryConfiguration = if (repositoryConfigurationFile.isFile) {
            repositoryConfigurationFile.readValue()
        } else {
            RepositoryConfiguration()
        }

        val ortResult = readOrtResult(ortFile)
            .replaceConfig(repositoryConfiguration)

        val generatedPathExcludes = ortResult
            .getProjects()
            .filterNot { ortResult.isExcluded(it.id) }
            .map { project ->
                PathExclude(
                    pattern = ortResult.getDefinitionFilePathRelativeToAnalyzerRoot(project),
                    reason = PathExcludeReason.OPTIONAL_COMPONENT_OF,
                    comment = "TODO"
                )
            }

        val existingPathExcludes = repositoryConfiguration.excludes.paths
        val pathExcludes = (existingPathExcludes + generatedPathExcludes).distinctBy { it.pattern }

        repositoryConfiguration
            .replacePathExcludes(pathExcludes)
            .sortPathExcludes()
            .write(repositoryConfigurationFile)
    }
}
