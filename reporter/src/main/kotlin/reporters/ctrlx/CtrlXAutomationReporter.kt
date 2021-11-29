/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.reporter.reporters.ctrlx

import java.io.File

import org.ossreviewtoolkit.model.licenses.LicenseView
import org.ossreviewtoolkit.model.writeValue
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.toSpdx

class CtrlXAutomationReporter : Reporter {
    companion object {
        const val REPORT_FILENAME = "fossinfo.json"

        private val LICENSE_NOASSERTION = License(
            name = SpdxConstants.NOASSERTION,
            spdx = SpdxConstants.NOASSERTION.toSpdx(),
            text = ""
        )
    }

    override val reporterName = "CtrlXAutomation"

    override fun generateReport(input: ReporterInput, outputDir: File, options: Map<String, String>): List<File> {
        val reportFile = outputDir.resolve(REPORT_FILENAME)

        val packages = input.ortResult.getPackages(omitExcluded = true)
        val components = packages.mapTo(mutableListOf()) { (pkg, _) ->
            val qualifiedName = when (pkg.id.type) {
                // At least for NPM packages, CtrlX requires the component name to be prefixed with the scope name,
                // separated with a slash. Other package managers might require similar handling, but there seems to be
                // no documentation about the expected separator character.
                "NPM" -> with(pkg.id) {
                    listOfNotNull(namespace.takeIf { it.isNotEmpty() }, name).joinToString("/")
                }

                else -> pkg.id.name
            }

            val resolvedLicenseInfo = input.licenseInfoResolver.resolveLicenseInfo(pkg.id).filterExcluded()
            val copyrights = resolvedLicenseInfo.getCopyrights().joinToString("\n").takeUnless { it.isEmpty() }
            val effectiveLicense = resolvedLicenseInfo.effectiveLicense(
                LicenseView.CONCLUDED_OR_DECLARED_AND_DETECTED,
                input.ortResult.getPackageLicenseChoices(pkg.id),
                input.ortResult.getRepositoryLicenseChoices()
            )
            val licenses = effectiveLicense?.decompose()?.map {
                val id = it.toString()
                val text = input.licenseTextProvider.getLicenseText(id)
                License(name = id, spdx = it, text = text.orEmpty())
            }

            // The specification requires at least one license.
            val componentLicenses = licenses.takeUnless { it.isNullOrEmpty() } ?: listOf(LICENSE_NOASSERTION)

            Component(
                name = qualifiedName,
                version = pkg.id.version,
                homepage = pkg.homepageUrl.takeUnless { it.isEmpty() },
                copyright = copyrights?.let { CopyrightInformation(it) },
                licenses = componentLicenses,
                usage = if (pkg.isModified) Usage.Modified else Usage.AsIs
                // TODO: Map the PackageLinkage to an IntegrationMechanism.
            )
        }

        val info = FossInfo(components = components)
        reportFile.writeValue(info)

        return listOf(reportFile)
    }
}
