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

package org.ossreviewtoolkit.plugins.reporters.freemarker

import java.io.File

import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterFactory
import org.ossreviewtoolkit.reporter.ReporterInput

data class PlainTextTemplateReporterConfig(
    /**
     * A comma-separated list of IDs of templates provided by ORT. Currently, only the "NOTICE_DEFAULT" and
     * "NOTICE_SUMMARY" templates are available.
     * If no template id or path is provided, the "NOTICE_DEFAULT" template is used.
     */
    @OrtPluginOption(aliases = ["template.id"])
    val templateIds: List<String>?,

    /**
     * A comma-separated list of paths to template files provided by the user.
     */
    @OrtPluginOption(aliases = ["template.path"])
    val templatePaths: List<String>?
) {
    companion object {
        internal val DEFAULT = PlainTextTemplateReporterConfig(
            templateIds = listOf("NOTICE_DEFAULT"),
            templatePaths = null
        )
    }
}

/**
 * A [Reporter] that creates plain text files using [Apache Freemarker][1] templates. For each template provided in the
 * [config], a separate output file is created. If no templates are provided, the "NOTICE_DEFAULT" template is used.
 * The name of the template id or template path (without extension) is used for the generated file, so be careful to not
 * use two different templates with the same name.
 *
 * [1]: https://freemarker.apache.org
 */
@OrtPlugin(
    displayName = "Plain Text Template",
    description = "Generates plain text files using Apache Freemarker templates.",
    factory = ReporterFactory::class
)
class PlainTextTemplateReporter(
    override val descriptor: PluginDescriptor = PlainTextTemplateReporterFactory.descriptor,
    private val config: PlainTextTemplateReporterConfig
) : Reporter {
    companion object {
        private const val TEMPLATE_DIRECTORY = "plain-text"
    }

    private val templateProcessor = FreemarkerTemplateProcessor(TEMPLATE_DIRECTORY)

    override fun generateReport(input: ReporterInput, outputDir: File): List<Result<File>> {
        val actualConfig = config.takeIf {
            it.templateIds?.isNotEmpty() == true || it.templatePaths?.isNotEmpty() == true
        } ?: PlainTextTemplateReporterConfig.DEFAULT

        return templateProcessor.processTemplates(
            input,
            outputDir,
            actualConfig.templateIds.orEmpty(),
            actualConfig.templatePaths.orEmpty()
        )
    }
}
