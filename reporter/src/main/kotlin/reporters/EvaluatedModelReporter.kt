/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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

import java.io.File
import java.io.Writer

import kotlin.time.measureTimedValue

import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.reporter.model.EvaluatedModel
import org.ossreviewtoolkit.utils.log
import org.ossreviewtoolkit.utils.perf

/**
 * Creates a JSON file containing the evaluated model.
 */
class EvaluatedModelJsonReporter : EvaluatedModelReporter(
    reporterName = "EvaluatedModelJson",
    reportFilename = "evaluated-model.json",
    serialize = EvaluatedModel::toJson
)

/**
 * Creates a YAML file containing the evaluated model.
 */
class EvaluatedModelYamlReporter : EvaluatedModelReporter(
    reporterName = "EvaluatedModelYaml",
    reportFilename = "evaluated-model.yml",
    serialize = EvaluatedModel::toYaml
)

/**
 * An abstract [Reporter] that generates an [EvaluatedModel]. The model is serialized using the provided [serialize]
 * function.
 */
abstract class EvaluatedModelReporter(
    override val reporterName: String,
    private val reportFilename: String,
    private val serialize: EvaluatedModel.(Writer) -> Unit
) : Reporter {
    override fun generateReport(
        input: ReporterInput,
        outputDir: File,
        options: Map<String, String>
    ): List<File> {
        val evaluatedModel = measureTimedValue { EvaluatedModel.create(input) }

        log.perf { "Generating evaluated model took ${evaluatedModel.duration.inMilliseconds}ms." }

        val outputFile = outputDir.resolve(reportFilename)

        outputFile.bufferedWriter().use {
            evaluatedModel.value.serialize(it)
        }

        return listOf(outputFile)
    }
}
