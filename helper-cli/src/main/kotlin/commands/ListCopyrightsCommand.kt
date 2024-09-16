/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.helper.utils.OrtHelperCommand
import org.ossreviewtoolkit.helper.utils.processAllCopyrightStatements
import org.ossreviewtoolkit.helper.utils.readOrtResult
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.config.CopyrightGarbage
import org.ossreviewtoolkit.model.config.orEmpty
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.plugins.packageconfigurationproviders.dir.DirPackageConfigurationProvider
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.config.setPackageConfigurations

internal class ListCopyrightsCommand : OrtHelperCommand(
    help = "Lists the copyright findings."
) {
    private val ortFile by option(
        "--ort-file", "-i",
        help = "The ORT result file to read as input."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val copyrightGarbageFile by option(
        "--copyright-garbage-file",
        help = "A file containing garbage copyright statements entries which are to be ignored."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }

    private val packageId by option(
        "--package-id",
        help = "The target package for which the copyrights shall be listed."
    ).convert { Identifier(it) }

    private val licenseId by option(
        "--license-id",
        help = "The license for which the copyrights shall be listed."
    )

    private val showRawStatements by option(
        "--show-raw-statements",
        help = "Show the raw statements corresponding to each processed statement if these are any different."
    ).flag()

    private val packageConfigurationsDir by option(
        "--package-configurations-dir",
        help = "A directory that is searched recursively for package configuration files. Each file must only " +
            "contain a single package configuration."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = false, canBeDir = true, mustBeWritable = false, mustBeReadable = true)

    override fun run() {
        val ortResult = readOrtResult(ortFile)
            .setPackageConfigurations(DirPackageConfigurationProvider(packageConfigurationsDir))
        val copyrightGarbage = copyrightGarbageFile?.readValue<CopyrightGarbage>().orEmpty()

        val copyrightStatements = ortResult.processAllCopyrightStatements(copyrightGarbage = copyrightGarbage.items)
            .filter { packageId == null || it.packageId == packageId }
            .filter { licenseId == null || it.license.toString() == licenseId }
            .groupBy({ it.statement }, { it.rawStatements })
            .mapValues { it.value.flatten().toSortedSet() }

        val result = buildString {
            copyrightStatements.toSortedMap().forEach { (processedStatement, unprocessedStatements) ->
                appendLine(processedStatement)
                if (showRawStatements && unprocessedStatements.size > 1) {
                    unprocessedStatements.sorted().forEach {
                        appendLine("  $it")
                    }
                }
            }
        }

        println(result)
    }
}
