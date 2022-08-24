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

package org.ossreviewtoolkit.helper.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.helper.utils.readOrtResult
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.PackageType
import org.ossreviewtoolkit.model.PackageType.PACKAGE
import org.ossreviewtoolkit.model.PackageType.PROJECT
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.licenses.LicenseView
import org.ossreviewtoolkit.model.utils.createLicenseInfoResolver
import org.ossreviewtoolkit.utils.common.expandTilde

class ListPackagesCommand : CliktCommand(
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
    ).enum<PackageType>().split(",").default(enumValues<PackageType>().asList())

    private val offendingOnly by option(
        "--offending-only",
        help = "Only list packages causing at least one rule violation with an offending severity, see " +
                "--offending-severities."
    ).flag()

    private val offendingSeverities by option(
        "--offending-severities",
        help = "Set the severities to use for the filtering enabled by --offending-only, specified as " +
                "comma-separated values."
    ).enum<Severity>().split(",").default(enumValues<Severity>().asList())

    override fun run() {
        val ortResult = readOrtResult(ortFile)

        val licenseInfoResolver = ortResult.createLicenseInfoResolver()

        fun getDetectedLicenses(id: Identifier): List<String> =
            licenseInfoResolver.resolveLicenseInfo(id)
                .filter(LicenseView.ONLY_DETECTED)
                .map { it.license.toString() }

        val packagesWithOffendingRuleViolations = ortResult.getRuleViolations().filter {
            it.severity in offendingSeverities
        }.mapNotNullTo(mutableSetOf()) { it.pkg }

        val packages = ortResult.collectProjectsAndPackages().filter { id ->
            (ortResult.isPackage(id) && PACKAGE in type) || (ortResult.isProject(id) && PROJECT in type)
        }.filter { id ->
            matchDetectedLicenses.isEmpty() || (matchDetectedLicenses - getDetectedLicenses(id)).isEmpty()
        }.filter { id ->
            !offendingOnly || id in packagesWithOffendingRuleViolations
        }.sortedBy { it }

        val result = buildString {
            packages.forEach {
                appendLine(it.toCoordinates())
            }
        }
        println(result)
    }
}
