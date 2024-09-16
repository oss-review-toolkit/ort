/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.helper.utils.OrtHelperCommand
import org.ossreviewtoolkit.helper.utils.VcsUrlMapping
import org.ossreviewtoolkit.helper.utils.findFilesRecursive
import org.ossreviewtoolkit.helper.utils.findRepositoryPaths
import org.ossreviewtoolkit.helper.utils.importPathExcludes
import org.ossreviewtoolkit.helper.utils.mergePathExcludes
import org.ossreviewtoolkit.helper.utils.orEmpty
import org.ossreviewtoolkit.helper.utils.sortPathExcludes
import org.ossreviewtoolkit.helper.utils.write
import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.utils.common.expandTilde

internal class ImportPathExcludesCommand : OrtHelperCommand(
    help = "Import path excludes by repository from a file into the given package configuration."
) {
    private val pathExcludesFile by option(
        "--path-excludes-file",
        help = "The input path excludes file."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val packageConfigurationFile by option(
        "--package-configuration-file",
        help = "The package configuration."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = true, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val sourceCodeDir by option(
        "--source-code-dir",
        help = "A directory containing the sources for the provenance matching the given package configuration."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val updateOnlyExisting by option(
        "--update-only-existing",
        help = "If enabled, only entries are imported for which an entry with the same pattern already exists."
    ).flag()

    private val vcsUrlMappingFile by option(
        "--vcs-url-mapping-file",
        help = "A YAML or JSON file containing a mapping of VCS URLs to other VCS URLs which will be replaced during " +
            "the import."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }

    override fun run() {
        val allFiles = findFilesRecursive(sourceCodeDir)
        val packageConfiguration = packageConfigurationFile.readValue<PackageConfiguration>()
        val vcsUrlMapping = vcsUrlMappingFile?.readValue<VcsUrlMapping>().orEmpty()

        val existingPathExcludes = packageConfiguration.pathExcludes
        val repositoryPaths = findRepositoryPaths(sourceCodeDir)
        val importedPathExcludes = importPathExcludes(repositoryPaths, pathExcludesFile, vcsUrlMapping)
            .filter { pathExclude -> allFiles.any { pathExclude.matches(it) } }

        val pathExcludes = existingPathExcludes
            .mergePathExcludes(importedPathExcludes, updateOnlyExisting)
            .sortPathExcludes()

        packageConfiguration
            .copy(pathExcludes = pathExcludes)
            .write(packageConfigurationFile)
    }
}
