/*
 * Copyright (C) 2019 HERE Europe B.V.
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

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.helper.common.getRepositoryPaths
import org.ossreviewtoolkit.helper.common.importPathExcludes
import org.ossreviewtoolkit.helper.common.mergePathExcludes
import org.ossreviewtoolkit.helper.common.readOrtResult
import org.ossreviewtoolkit.helper.common.replacePathExcludes
import org.ossreviewtoolkit.helper.common.sortPathExcludes
import org.ossreviewtoolkit.helper.common.write
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.utils.common.expandTilde

internal class ImportPathExcludesCommand : CliktCommand(
    help = "Import path excludes by repository from a file into the given repository configuration."
) {
    private val pathExcludesFile by option(
        "--path-excludes-file", "-i",
        help = "The input path excludes file."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val ortFile by option(
        "--ort-file",
        help = "The ORT file containing the findings the imported path excludes need to match against."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val repositoryConfigurationFile by option(
        "--repository-configuration-file",
        help = "The repository configuration file where the imported path excludes are to be merged into."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val updateOnlyExisting by option(
        "--update-only-existing",
        help = "If enabled, only entries are imported for which an entry with the same pattern already exists."
    ).flag()

    override fun run() {
        val ortResult = readOrtResult(ortFile)
        val allFiles = ortResult.getProjectFindingFiles()

        val repositoryConfiguration = if (repositoryConfigurationFile.isFile) {
            repositoryConfigurationFile.readValue()
        } else {
            RepositoryConfiguration()
        }

        val existingPathExcludes = repositoryConfiguration.excludes.paths
        val repositoryPaths = ortResult.getRepositoryPaths()
        val importedPathExcludes = importPathExcludes(repositoryPaths, pathExcludesFile).filter { pathExclude ->
            allFiles.any { pathExclude.matches(it) }
        }

        val pathExcludes = existingPathExcludes.mergePathExcludes(importedPathExcludes, updateOnlyExisting)

        repositoryConfiguration
            .replacePathExcludes(pathExcludes)
            .sortPathExcludes()
            .write(repositoryConfigurationFile)
    }
}

private fun OrtResult.getProjectFindingFiles(): Set<String> {
    val result = mutableSetOf<String>()
    val repositoryPaths = getRepositoryPaths()

    getProjects().forEach { project ->
        getScanResultsForId(project.id).forEach { scanResult ->
            val vcsUrl = (scanResult.provenance as RepositoryProvenance).vcsInfo.url

            repositoryPaths.getValue(vcsUrl).forEach { repositoryPath ->
                with(scanResult.summary) {
                    licenseFindings.mapTo(result) { "$repositoryPath/${it.location.path}" }
                    copyrightFindings.mapTo(result) { "$repositoryPath/${it.location.path}" }
                }
            }
        }
    }

    return result
}
