/*
 * Copyright (C) 2021 HERE Europe B.V.
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

package org.ossreviewtoolkit.helper.commands.packageconfig

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.helper.common.RepositoryPathExcludes
import org.ossreviewtoolkit.helper.common.VcsUrlMapping
import org.ossreviewtoolkit.helper.common.findRepositories
import org.ossreviewtoolkit.helper.common.getPathExcludesByRepository
import org.ossreviewtoolkit.helper.common.mapPathExcludesVcsUrls
import org.ossreviewtoolkit.helper.common.mergePathExcludes
import org.ossreviewtoolkit.helper.common.orEmpty
import org.ossreviewtoolkit.helper.common.write
import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.utils.common.expandTilde

class ExportPathExcludesCommand : CliktCommand(
    help = "Export the path excludes to a path excludes file which maps repository URLs to the path excludes for the " +
            "respective repository"
) {
    private val pathExcludesFile by option(
        "--path-excludes-file",
        help = "The output path excludes file."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val packageConfigurationFile by option(
        "--package-configuration-file",
        help = "The package configuration."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val sourceCodeDir by option(
        "--source-code-dir",
        help = "A directory containing the sources corresponding to the provided package configuration."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val updateOnlyExisting by option(
        "--update-only-existing",
        help = "If enabled, only entries are exported for which an entry with the same pattern already exists."
    ).flag()

    private val vcsUrlMappingFile by option(
        "--vcs-url-mapping-file",
        help = "A YAML or JSON file containing a mapping of VCS URLs to other VCS URLs which will be replaced during " +
                "the export."
    ).file(mustExist = false, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }

    override fun run() {
        val vcsUrlMapping = vcsUrlMappingFile?.readValue<VcsUrlMapping>().orEmpty()

        val globalPathExcludes: RepositoryPathExcludes = if (pathExcludesFile.isFile) {
            pathExcludesFile.readValue<RepositoryPathExcludes>()
        } else {
            mapOf()
        }.mapPathExcludesVcsUrls(vcsUrlMapping)

        val localPathExcludes = getPathExcludesByRepository(
            pathExcludes = packageConfigurationFile.readValue<PackageConfiguration>().pathExcludes,
            nestedRepositories = findRepositories(sourceCodeDir)
        ).mapPathExcludesVcsUrls(vcsUrlMapping)

        globalPathExcludes
            .mergePathExcludes(localPathExcludes, updateOnlyExisting = updateOnlyExisting)
            .write(pathExcludesFile)
    }
}
