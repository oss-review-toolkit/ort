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
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.helper.common.getUnresolvedRuleViolations
import org.ossreviewtoolkit.helper.common.readOrtResult
import org.ossreviewtoolkit.helper.common.replaceRuleViolationResolutions
import org.ossreviewtoolkit.helper.common.write
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.RuleViolationResolution
import org.ossreviewtoolkit.model.config.RuleViolationResolutionReason
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.utils.common.expandTilde

internal class GenerateRuleViolationResolutionsCommand : CliktCommand(
    help = "Generates resolutions for all unresolved rule violations. The output is written to the given repository " +
            "configuration file."
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
        help = "Override the repository configuration contained in the given input ORT file."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val severity by option(
        "--severity",
        help = "Only consider violations of the given severities, specified as comma-separated values. Allowed " +
                "values: ERROR,WARNING,HINT."
    ).enum<Severity>().split(",").default(enumValues<Severity>().asList())

    override fun run() {
        val repositoryConfiguration = repositoryConfigurationFile.readValue<RepositoryConfiguration>()
        val ortResult = readOrtResult(ortFile).replaceConfig(repositoryConfiguration)

        val generatedResolutions = ortResult
            .getUnresolvedRuleViolations()
            .filter { it.severity in severity }
            .map {
                RuleViolationResolution(
                    message = it.message,
                    reason = RuleViolationResolutionReason.CANT_FIX_EXCEPTION,
                    comment = "TODO"
                )
            }

        val resolutions = repositoryConfiguration.resolutions.ruleViolations + generatedResolutions

        repositoryConfiguration
            .replaceRuleViolationResolutions(resolutions)
            .write(repositoryConfigurationFile)
    }
}
