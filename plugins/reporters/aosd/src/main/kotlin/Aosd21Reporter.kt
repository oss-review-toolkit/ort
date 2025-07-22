/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.reporters.aosd

import java.io.File

import org.ossreviewtoolkit.model.CuratedPackage
import org.ossreviewtoolkit.model.DependencyNavigator
import org.ossreviewtoolkit.model.DependencyNode
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.licenses.LicenseView
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.reporters.aosd.AOSD21.Component
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterFactory
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.spdx.nullOrBlankToSpdxNoassertionOrNone

@OrtPlugin(
    id = "AOSD2.1",
    displayName = "Audi Open Source Diagnostics 2.1",
    description = "A reporter for the Audi Open Source Diagnostics (AOSD) 2.1 format.",
    factory = ReporterFactory::class
)
class Aosd21Reporter(override val descriptor: PluginDescriptor = Aosd21ReporterFactory.descriptor) : Reporter {
    override fun generateReport(input: ReporterInput, outputDir: File): List<Result<File>> {
        val reportFiles = input.ortResult.getProjects(omitExcluded = true).map { project ->
            val indexedPackages = input.ortResult.getPackages(omitExcluded = true).withIndex().associateBy {
                it.value.metadata.id
            }

            val directDependencies = input.ortResult.getDependencies(project.id, maxLevel = 1, omitExcluded = true)

            runCatching {
                // Do not validate the AOSD21 object here as it has the impracticable requirement to contain at least
                // one direct dependency / component.
                val model = AOSD21(
                    externalId = project.id.toCoordinates(),
                    directDependencies = directDependencies.mapNotNullTo(mutableSetOf()) {
                        indexedPackages[it]?.index?.toLong()
                    },
                    components = indexedPackages.toComponents(input, project)
                )

                val projectName = project.id.toPath("-")

                outputDir.resolve("aosd21.$projectName.json").writeReport(model)
            }
        }

        return reportFiles
    }
}

private fun Map<Identifier, IndexedValue<CuratedPackage>>.toComponents(
    input: ReporterInput,
    project: Project
): Set<Component> =
    values.mapTo(mutableSetOf()) { (index, pkg) ->
        val dependencies = input.ortResult.getDependencies(pkg.metadata.id, maxLevel = 1, omitExcluded = true)
        val node = input.ortResult.dependencyNavigator.findBreadthFirst(project, pkg.metadata.id)

        val nonExcludedLicenseInfo = input.licenseInfoResolver.resolveLicenseInfo(pkg.metadata.id).filterExcluded()
        val relevantLicenseInfo = nonExcludedLicenseInfo.filter(LicenseView.CONCLUDED_OR_DECLARED_AND_DETECTED)

        // Use an unsimplified expression here to better document where a license selection might come from.
        val licenseExpression = relevantLicenseInfo.toExpression()?.sorted()

        val selectedLicenseInfo = relevantLicenseInfo
            .applyChoices(input.ortResult.getPackageLicenseChoices(pkg.metadata.id))
            .applyChoices(input.ortResult.getRepositoryLicenseChoices())

        val selectedExpression = selectedLicenseInfo.toExpression()?.simplify()?.sorted()
            ?.takeUnless { it.offersChoice() }

        val licenseTexts = licenseExpression?.licenses().orEmpty().mapNotNullTo(mutableSetOf()) { license ->
            input.licenseFactProvider.getLicenseText(license)
        }.joinToString("\n--\n") { it.trimEnd() }

        with(pkg.metadata) {
            Component(
                id = index.toLong(),
                componentName = id.name,
                componentVersion = id.version,
                scmUrl = vcsProcessed.url.takeUnless { it.isEmpty() } ?: homepageUrl,
                modified = isModified,
                // For simplicity, only use the first linkage found in the graph for the package.
                linking = node?.linkage?.toLinking(),
                transitiveDependencies = dependencies.mapNotNullTo(mutableSetOf()) { get(it)?.index?.toLong() },
                subcomponents = listOf(
                    AOSD21.Subcomponent(
                        subcomponentName = FIRST_SUBCOMPONENT_NAME,
                        spdxId = licenseExpression.nullOrBlankToSpdxNoassertionOrNone(),
                        copyrights = relevantLicenseInfo.getCopyrights().sorted(),
                        authors = pkg.metadata.authors.sorted(),
                        licenseText = licenseTexts,
                        // Can be empty as the license information is the result of a file level scan.
                        licenseTextUrl = "",
                        selectedLicense = selectedExpression?.toString().orEmpty()
                    ).validate()
                )
            ).validate()
        }
    }

private fun DependencyNavigator.findBreadthFirst(project: Project, nodeId: Identifier): DependencyNode? {
    fun Sequence<DependencyNode>.findBreadthFirst(id: Identifier): DependencyNode? {
        // This also turns the sequence into a list so it can be consumed twice, see below.
        val directDependencies = mapTo(mutableListOf()) { it.getStableReference() }

        directDependencies.find { node -> node.id == id }?.also { return it }

        return directDependencies.firstNotNullOfOrNull { node ->
            node.visitDependencies { it.findBreadthFirst(id) }
        }
    }

    return scopeNames(project).asSequence().mapNotNull { scopeName ->
        directDependencies(project, scopeName).findBreadthFirst(nodeId)
    }.firstOrNull()
}

private fun PackageLinkage.toLinking(): String? =
    when (this) {
        PackageLinkage.DYNAMIC -> "dynamic_linking"
        PackageLinkage.STATIC -> "static_linking"
        else -> null
    }
