/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package org.ossreviewtoolkit.reporter.reporters

import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.reporter.model.EvaluatedModel

import java.io.OutputStream

class WebAppReporter : Reporter {
    override val reporterName = "WebApp"
    override val defaultFilename = "scan-report-web-app.html"

    override fun generateReport(
        outputStream: OutputStream,
        input: ReporterInput
    ) {
        val template = javaClass.classLoader.getResource("scan-report-template.html").readText()
        val evaluatedModel = EvaluatedModel.create(input)

        val placeholder = "ORT_REPORT_DATA_PLACEHOLDER"
        val index = template.indexOf(placeholder)
        val prefix = template.substring(0, index)
        val suffix = template.substring(index + placeholder.length, template.length)

        outputStream.bufferedWriter().use {
            it.write(prefix)
            evaluatedModel.toJson(it, prettyPrint = false)
            it.write(suffix)
        }
    }
}
