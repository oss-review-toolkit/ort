/*
 * Copyright (C) 2017-2010 HERE Europe B.V.
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

package com.here.ort.reporter.reporters

import com.fasterxml.jackson.databind.ObjectMapper

import com.here.ort.model.jsonMapper
import com.here.ort.model.yamlMapper
import com.here.ort.reporter.utils.StatisticsCalculator
import com.here.ort.reporter.Reporter
import com.here.ort.reporter.ReporterInput
import com.here.ort.reporter.model.EvaluatedModel

import java.io.OutputStream

/**
 * Creates a JSON file containing the evaluated model.
 */
class EvaluatedModelJsonReporter : EvaluatedModelReporter(
    reporterName = "EvaluatedModelJson",
    defaultFileName = "evaluated-model-report.json",
    mapper = jsonMapper
)

/**
 * Creates a YAML file containing the evaluated model.
 */
class EvaluatedModelYamlReporter : EvaluatedModelReporter(
    reporterName = "EvaluatedModelYaml",
    defaultFileName = "evaluated-model-report.yml",
    mapper = yamlMapper
)

/**
 * Creates a file containing the evaluated model using the given [mapper].
 */
abstract class EvaluatedModelReporter(
    reporterName: String,
    defaultFileName: String,
    private val mapper: ObjectMapper
) : Reporter {
    override val reporterName = reporterName
    override val defaultFilename = defaultFileName

    override fun generateReport(outputStream: OutputStream, input: ReporterInput) {
        val evaluatedModel = EvaluatedModel(
            stats = StatisticsCalculator().getStatistics(input.ortResult, input.resolutionProvider)
        )

        outputStream.bufferedWriter().use {
            it.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(evaluatedModel))
        }
    }
}
