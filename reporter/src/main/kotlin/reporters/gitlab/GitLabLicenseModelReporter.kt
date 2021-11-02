/*
 * Copyright (C) 2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.reporter.reporters.gitlab

import java.io.File

import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.utils.common.isTrue

/**
 * Creates YAML documents according to the GitLab license model schema version 2.1, see
 * https://gitlab.com/gitlab-org/security-products/license-management/-/blob/master/spec/fixtures/schema/v2.1.json.
 * Examples can be found under
 * https://gitlab.com/gitlab-org/security-products/license-management/-/tree/master/spec/fixtures/expected.
 *
 * This reporter supports the following options:
 * - *skip.excluded*: Set to 'true' to omit excluded packages in the report. Defaults to 'false'.
 */
class GitLabLicenseModelReporter : Reporter {
    companion object {
        const val OPTION_SKIP_EXCLUDED = "skip.excluded"
    }

    override val reporterName = "GitLabLicenseModel"

    private val reportFilename = "gl-license-scanning-report.json"

    override fun generateReport(
        input: ReporterInput,
        outputDir: File,
        options: Map<String, String>
    ): List<File> {
        val skipExcluded = options[OPTION_SKIP_EXCLUDED].isTrue()

        val licenseModel = GitLabLicenseModelMapper.map(input.ortResult, skipExcluded)
        val licenseModelJson = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(licenseModel)

        val outputFile = outputDir.resolve(reportFilename)
        outputFile.bufferedWriter().use { it.write(licenseModelJson) }

        return listOf(outputFile)
    }
}
