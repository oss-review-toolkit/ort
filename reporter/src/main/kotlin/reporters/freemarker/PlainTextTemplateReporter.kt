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

package org.ossreviewtoolkit.reporter.reporters.freemarker

import java.io.File

import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput

/**
 * A [Reporter] that creates plain text files using [Apache Freemarker][1] templates. For each template provided using
 * the options described below a separate output file is created. If no options are provided the "NOTICE_DEFAULT"
 * template is used. The name of the template id or template path (without extension) is used for the generated file, so
 * be careful to not use two different templates with the same name.
 *
 * This reporter supports the following options:
 * - *template.id*: A comma-separated list of IDs of templates provided by ORT. Currently, only the "NOTICE_DEFAULT"
 *                  and "NOTICE_SUMMARY" templates are available.
 * - *template.path*: A comma-separated list of paths to template files provided by the user.
 * - *project-types-as-packages: A comma-separated list of project types to be handled as packages.
 *
 * [1]: https://freemarker.apache.org
 */
class PlainTextTemplateReporter : Reporter {
    companion object {
        private const val TEMPLATE_DIRECTORY = "plain-text"

        private const val DEFAULT_TEMPLATE_ID = "NOTICE_DEFAULT"
    }

    override val type = "PlainTextTemplate"

    private val templateProcessor = FreemarkerTemplateProcessor(TEMPLATE_DIRECTORY)

    override fun generateReport(input: ReporterInput, outputDir: File, options: Map<String, String>): List<File> {
        val templateOptions = options.toMutableMap()

        if (FreemarkerTemplateProcessor.OPTION_TEMPLATE_PATH !in templateOptions) {
            templateOptions.putIfAbsent(FreemarkerTemplateProcessor.OPTION_TEMPLATE_ID, DEFAULT_TEMPLATE_ID)
        }

        return templateProcessor.processTemplates(input, outputDir, templateOptions)
    }
}
