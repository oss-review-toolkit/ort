/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.reporters.ctrlx

import java.io.File

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream

import org.ossreviewtoolkit.model.licenses.LicenseView
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterFactory
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.SpdxLicense
import org.ossreviewtoolkit.utils.spdx.toSpdx

data class CtrlXAutomationReporterConfig(
    /**
     * The categories of the licenses of the packages to include in the report. If a component has a license which has a
     * category not present in this parameter, the license is removed from the component and not visible in the report.
     * If a component has ALL its licenses removed this way, it is not displayed in the report. If the parameter is not
     * set for the reporter, all components and all licenses are present in the report.
     */
    val licenseCategoriesToInclude: List<String>?
)

@OrtPlugin(
    displayName = "CtrlX Automation",
    description = "A reporter for the ctrlX Automation format.",
    factory = ReporterFactory::class
)
class CtrlXAutomationReporter(
    override val descriptor: PluginDescriptor = CtrlXAutomationReporterFactory.descriptor,
    private val config: CtrlXAutomationReporterConfig
) :
    Reporter {
    companion object {
        const val REPORT_FILENAME = "fossinfo.json"

        val JSON = Json.Default

        private val LICENSE_NOASSERTION = License(
            name = SpdxConstants.NOASSERTION,
            spdx = SpdxConstants.NOASSERTION,
            text = ""
        )
    }

    override fun generateReport(input: ReporterInput, outputDir: File): List<Result<File>> {
        val packages = input.ortResult.getPackages(omitExcluded = true)
        val licensesToInclude = config.licenseCategoriesToInclude?.flatMap {
            input.licenseClassifications.licensesByCategory[it].orEmpty()
        }.orEmpty()

        val components = packages.mapNotNullTo(mutableListOf()) { (pkg, _) ->
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
            var licenses = effectiveLicense?.decompose()?.map {
                val name = it.toString()
                val spdxId = SpdxLicense.forId(name)?.id
                val text = input.licenseFactProvider.getLicenseText(name)
                License(name = name, spdx = spdxId, text = text.orEmpty())
            }

            var componentShouldBeExcluded = false

            if (config.licenseCategoriesToInclude != null) {
                val filteredLicenses = licenses?.filter { it.name.toSpdx() in licensesToInclude }

                if (filteredLicenses != null && filteredLicenses.isEmpty()) {
                    componentShouldBeExcluded = true
                } else {
                    licenses = filteredLicenses
                }
            }

            if (componentShouldBeExcluded) {
                null
            } else {
                // The specification requires at least one license.
                val componentLicenses = licenses.orEmpty().ifEmpty { listOf(LICENSE_NOASSERTION) }

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
        }

        val reportFileResult = runCatching {
            val info = FossInfo(components = components)

            outputDir.resolve(REPORT_FILENAME).apply {
                outputStream().use { JSON.encodeToStream(info, it) }
            }
        }

        return listOf(reportFileResult)
    }
}
