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

import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterInput
import org.ossreviewtoolkit.reporter.model.EvaluatedModel
import org.ossreviewtoolkit.utils.log

import java.io.OutputStream
import java.io.Writer

private fun EvaluatedModel.toJson(writer: Writer) = toJson(writer, prettyPrint = true)

/**
 * Creates a JSON file containing the evaluated model.
 */
class EvaluatedModelJsonReporter : EvaluatedModelReporter(
    reporterName = "EvaluatedModelJson",
    defaultFilename = "evaluated-model.json",
    serialize = EvaluatedModel::toJson
)

/**
 * Creates a YAML file containing the evaluated model.
 */
class EvaluatedModelYamlReporter : EvaluatedModelReporter(
    reporterName = "EvaluatedModelYaml",
    defaultFilename = "evaluated-model.yml",
    serialize = EvaluatedModel::toYaml
)

/**
 * An abstract [Reporter] that generates an [EvaluatedModel]. The model is serialized using the provided [serialize]
 * function.
 */
abstract class EvaluatedModelReporter(
    override val reporterName: String,
    override val defaultFilename: String,
    private val serialize: EvaluatedModel.(Writer) -> Unit
) : Reporter {
    override fun generateReport(outputStream: OutputStream, input: ReporterInput, options: Map<String, String>) {
        val start = System.currentTimeMillis()
        val evaluatedModel = EvaluatedModel.create(input)
        log.debug { "Generating evaluated model took ${System.currentTimeMillis() - start}ms" }

        outputStream.bufferedWriter().use {
            evaluatedModel.serialize(it)
        }
    }
}
