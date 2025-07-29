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
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterFactory
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.common.div

@OrtPlugin(
    displayName = "TrustSource",
    description = "Generates a report in the TrustSource format.",
    factory = ReporterFactory::class
)
class TrustSourceReporter(override val descriptor: PluginDescriptor = TrustSourceReporterFactory.descriptor) :
    Reporter {
    companion object {
        val JSON = Json.Default
    }

    private val reportFilename = "trustsource-report.json"

    override fun generateReport(input: ReporterInput, outputDir: File): List<Result<File>> {
        val outputFile = outputDir / reportFilename

        val nav = input.ortResult.dependencyNavigator
        val scans = input.ortResult.getProjects().map { project ->
            val deps = nav.scopeNames(project).flatMap { traverseDeps(input, nav.directDependencies(project, it)) }
            NewScan(module = project.id.name, dependencies = deps)
        }

        val reportFileResult = runCatching {
            outputFile.apply {
                outputStream().use { JSON.encodeToStream(scans, it) }
            }
        }

        return listOf(reportFileResult)
    }
}

private fun traverseDeps(input: ReporterInput, deps: Sequence<DependencyNode>): List<Dependency> {
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
            License(name, url)
        }

        val checksum = pkg?.metadata?.binaryArtifact?.hash?.let { mapOf(it.algorithm.name to it.value) }

        val depPkg = pkg?.metadata?.sourceArtifact?.let {
            Package(
                sourcesUrl = it.url,
                sourcesChecksum = mapOf(it.hash.algorithm.name to it.hash.value)
            )
        }

        Dependency(
            name = dep.id.name,
            purl = pkg?.metadata?.purl.orEmpty(),
            repositoryUrl = pkg?.metadata?.vcs?.url.orEmpty(),
            homepageUrl = pkg?.metadata?.homepageUrl.orEmpty(),
            description = pkg?.metadata?.description.orEmpty(),
            checksum = checksum.orEmpty(),
            dependencies = dep.visitDependencies { traverseDeps(input, it) },
            licenses = licenses.orEmpty(),
            pkg = depPkg
        )
    }

    return tsDeps.toList()
}
