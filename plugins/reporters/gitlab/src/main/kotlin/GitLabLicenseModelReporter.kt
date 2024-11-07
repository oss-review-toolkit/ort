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

package org.ossreviewtoolkit.plugins.reporters.gitlab

import java.io.File

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterFactory
import org.ossreviewtoolkit.reporter.ReporterInput

data class GitLabLicenseModelReporterConfig(
    /**
     * If true, excluded packages are omitted in the report.
     */
    @OrtPluginOption(defaultValue = "false")
    val skipExcluded: Boolean
)

/**
 * Creates YAML documents according to the GitLab license model schema version 2.1, see
 * https://gitlab.com/gitlab-org/security-products/license-management/-/blob/master/spec/fixtures/schema/v2.1.json.
 * Examples can be found under
 * https://gitlab.com/gitlab-org/security-products/license-management/-/tree/master/spec/fixtures/expected.
 */
@OrtPlugin(
    displayName = "GitLab License Model Reporter",
    description = "Creates YAML documents according to the GitLab license model schema version 2.1.",
    factory = ReporterFactory::class
)
class GitLabLicenseModelReporter(
    override val descriptor: PluginDescriptor = GitLabLicenseModelReporterFactory.descriptor,
    private val config: GitLabLicenseModelReporterConfig
) : Reporter {
    companion object {
        private val JSON = Json {
            encodeDefaults = true
            namingStrategy = JsonNamingStrategy.SnakeCase
            prettyPrint = true
            prettyPrintIndent = "  "
        }
    }

    private val reportFilename = "gl-license-scanning-report.json"

    override fun generateReport(input: ReporterInput, outputDir: File): List<Result<File>> {
        val licenseModel = GitLabLicenseModelMapper.map(input.ortResult, config.skipExcluded)

        val reportFileResult = runCatching {
            val licenseModelJson = JSON.encodeToString(licenseModel)

            outputDir.resolve(reportFilename).apply {
                bufferedWriter().use { it.write(licenseModelJson) }
            }
        }

        return listOf(reportFileResult)
    }
}
