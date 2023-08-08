/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.reporters.trustsource

import java.io.File

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream

import org.ossreviewtoolkit.model.DependencyNode
import org.ossreviewtoolkit.model.licenses.LicenseView
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput

class TrustSourceReporter : Reporter {
    companion object {
        val JSON = Json { encodeDefaults = false }
    }

    override val type = "TrustSource"

    private val reportFilename = "trustsource-report.json"

    override fun generateReport(input: ReporterInput, outputDir: File, options: Map<String, String>): List<File> {
        val outputFile = outputDir.resolve(reportFilename)

        val nav = input.ortResult.dependencyNavigator
        val modules = input.ortResult.getProjects().map { project ->
            val tsModuleDependencies = nav.scopeNames(project)
                .flatMap { traverseDeps(input, nav.directDependencies(project, it)) }

            TrustSourceModule(
                module = project.id.name,
                moduleId = "${project.id.type}:${project.id.name}",
                dependencies = tsModuleDependencies
            )
        }

        outputFile.outputStream().use { JSON.encodeToStream(modules, it) }

        return listOf(outputFile)
    }
}

private fun traverseDeps(input: ReporterInput, deps: Sequence<DependencyNode>): List<TrustSourceDependency> {
    val tsDeps = deps.map { dep ->
        val pkg = input.ortResult.getPackage(dep.id)

        val resolvedLicenseInfo = input.licenseInfoResolver.resolveLicenseInfo(dep.id).filterExcluded()
        val effectiveLicense = resolvedLicenseInfo.effectiveLicense(
            LicenseView.CONCLUDED_OR_DECLARED_AND_DETECTED,
            input.ortResult.getPackageLicenseChoices(dep.id),
            input.ortResult.getRepositoryLicenseChoices()
        )
        val licenses = effectiveLicense?.decompose()?.map {
            val name = it.toString()
            val url = it.getLicenseUrl().orEmpty()

            TrustSourceLicense(name, url)
        }

        TrustSourceDependency(
            key = "${dep.id.type}:${dep.id.name}",
            name = dep.id.name,
            repoUrl = pkg?.metadata?.sourceArtifact?.url.orEmpty(),
            homepageUrl = pkg?.metadata?.homepageUrl.orEmpty(),
            description = pkg?.metadata?.description.orEmpty(),
            checksum = "",
            private = false,

            versions = listOf(dep.id.version),

            dependencies = dep.visitDependencies { traverseDeps(input, it) },
            licenses = licenses.orEmpty(),
            meta = TrustSourceMeta()
        )
    }

    return tsDeps.toList()
}
