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

package org.ossreviewtoolkit.plugins.reporters.asciidoc

import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterFactory

/**
 * A [Reporter] that creates HTML files from [Apache Freemarker][1] templates.
 *
 * [1]: https://freemarker.apache.org
 */
@OrtPlugin(
    displayName = "HTML Template",
    description = "Generates HTML from AsciiDoc files from Apache Freemarker templates.",
    factory = ReporterFactory::class
)
class HtmlTemplateReporter(
    override val descriptor: PluginDescriptor = HtmlTemplateReporterFactory.descriptor,
    config: AsciiDocTemplateReporterConfig
) : AsciiDocTemplateReporter(config) {
    override val backend = "html"
}
