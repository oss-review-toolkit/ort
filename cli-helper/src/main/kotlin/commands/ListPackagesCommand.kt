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

package org.ossreviewtoolkit.helper.commands

import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.helper.utils.OrtHelperCommand
import org.ossreviewtoolkit.helper.utils.readOrtResult
import org.ossreviewtoolkit.helper.utils.replaceConfig
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.PackageType
import org.ossreviewtoolkit.model.PackageType.PACKAGE
import org.ossreviewtoolkit.model.PackageType.PROJECT
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.licenses.LicenseView
import org.ossreviewtoolkit.model.utils.createLicenseInfoResolver
import org.ossreviewtoolkit.utils.common.expandTilde

internal class ListPackagesCommand : OrtHelperCommand(
    help = "Lists the packages and optionally also projects contained in the given ORT result file."
) {
    private val ortFile by option(
        "--ort-file", "-i",
        help = "The ORT result file to read as input."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val matchDetectedLicenses by option(
        "--match-detected-licenses",
        help = "Omit all packages not matching all licenses given by this comma separated list of license identifiers."
    ).split(",").default(emptyList())

    private val type by option(
        "--package-type",
        help = "Filter the output by package type."
    ).enum<PackageType>().split(",").default(PackageType.entries)

    private val offendingOnly by option(
        "--offending-only",
        help = "Only list packages causing at least one rule violation with an offending severity, see " +
            "--offending-severities."
    ).flag()

    private val offendingSeverities by option(
        "--offending-severities",
        help = "Set the severities to use for the filtering enabled by --offending-only, specified as " +
            "comma-separated values."
    ).enum<Severity>().split(",").default(Severity.entries)

    private val omitExcluded by option(
        "--omit-excluded",
        help = "Only list non-excluded packages."
    ).flag()

    private val omitVersion by option(
        "--omit-version",
        help = "Only list packages distinct by type, namespace and name."
    ).flag()

    private val repositoryConfigurationFile by option(
        "--repository-configuration-file",
        help = "Override the repository configuration contained in the ORT result."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }

    override fun run() {
        val ortResult = readOrtResult(ortFile).replaceConfig(repositoryConfigurationFile)

        val licenseInfoResolver = ortResult.createLicenseInfoResolver()

        fun getDetectedLicenses(id: Identifier): List<String> =
            licenseInfoResolver.resolveLicenseInfo(id)
                .filter(LicenseView.ONLY_DETECTED)
                .map { it.license.toString() }

        val packagesWithOffendingRuleViolations = ortResult.getRuleViolations().filter {
            it.severity in offendingSeverities
        }.mapNotNullTo(mutableSetOf()) { it.pkg }

        val packages = ortResult.getProjectsAndPackages(omitExcluded = omitExcluded).filter { id ->
            (ortResult.isPackage(id) && PACKAGE in type) || (ortResult.isProject(id) && PROJECT in type)
        }.filter { id ->
            matchDetectedLicenses.isEmpty() || (matchDetectedLicenses - getDetectedLicenses(id)).isEmpty()
        }.filter { id ->
            !offendingOnly || id in packagesWithOffendingRuleViolations
        }.map {
            it.takeUnless { omitVersion } ?: it.copy(version = "")
        }.distinct().sortedBy { it }

        val result = buildString {
            packages.forEach {
                appendLine(it.toCoordinates())
            }
        }

        print(result)
    }
}
