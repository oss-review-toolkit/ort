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

package org.ossreviewtoolkit.plugins.reporters.fossid

import java.io.File

import org.ossreviewtoolkit.model.config.PluginConfiguration
import org.ossreviewtoolkit.plugins.reporters.asciidoc.HtmlTemplateReporter
import org.ossreviewtoolkit.plugins.reporters.freemarker.FreemarkerTemplateProcessor
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput

class FossIdSnippetReporter : Reporter by delegateReporter {
    companion object {
        private const val TEMPLATE_NAME = "fossid_snippet"

        private val delegateReporter = HtmlTemplateReporter()
    }

    override val type = "FossIdSnippet"

    override fun generateReport(
        input: ReporterInput,
        outputDir: File,
        config: PluginConfiguration
    ): List<Result<File>> {
        val hasFossIdResults = input.ortResult.scanner?.scanResults?.any { it.scanner.name == "FossId" } == true
        require(hasFossIdResults) { "No FossID scan results have been found." }

        val extendedOptions = config.copy(
            options = config.options + (FreemarkerTemplateProcessor.OPTION_TEMPLATE_ID to TEMPLATE_NAME)
        )
        return delegateReporter.generateReport(input, outputDir, extendedOptions)
    }
}
