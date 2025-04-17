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

import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.reporters.asciidoc.AsciiDocTemplateReporterConfig
import org.ossreviewtoolkit.plugins.reporters.asciidoc.HtmlTemplateReporter
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterFactory
import org.ossreviewtoolkit.reporter.ReporterInput

@OrtPlugin(
    displayName = "FossID Snippet",
    description = "Generates a detailed report of the FossID snippet findings.",
    factory = ReporterFactory::class
)
class FossIdSnippetReporter(override val descriptor: PluginDescriptor = FossIdSnippetReporterFactory.descriptor) :
    Reporter by delegateReporter {
    companion object {
        private val delegateReporter = HtmlTemplateReporter(
            FossIdSnippetReporterFactory.descriptor,
            AsciiDocTemplateReporterConfig(templateIds = listOf("fossid_snippet"), templatePaths = null)
        )
    }

    override fun generateReport(input: ReporterInput, outputDir: File): List<Result<File>> {
        val hasFossIdResults = input.ortResult.scanner?.scanResults?.any { it.scanner.name == "FossId" } == true
        require(hasFossIdResults) { "No FossID scan results have been found." }

        return delegateReporter.generateReport(input, outputDir)
    }
}
