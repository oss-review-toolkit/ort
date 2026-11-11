/*
 * Copyright (C) 2024 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.reporters.aosd

import java.io.File

import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.reporter.Reporter
import org.ossreviewtoolkit.reporter.ReporterFactory
import org.ossreviewtoolkit.reporter.ReporterInput

@OrtPlugin(
    id = "AOSD2.1",
    displayName = "Audi Open Source Diagnostics 2.1",
    summary = "A reporter for the Audi Open Source Diagnostics (AOSD) 2.1 format.",
    factory = ReporterFactory::class
)
class Aosd21Reporter(override val descriptor: PluginDescriptor = Aosd21ReporterFactory.descriptor) : Reporter {
    override fun generateReport(input: ReporterInput, outputDir: File): List<Result<File>> {
        val reportFiles = input.ortResult.getProjects(omitExcluded = true).map { project ->
            runCatching {
                // Do not validate the AOSD21 object here as it has the impracticable requirement to contain at least
                // one direct dependency / component.
                val model = createAosdModel(input, project)

                val projectName = project.id.toPath("-")

                outputDir.resolve("aosd21.$projectName.json").writeReport(model)
            }
        }

        return reportFiles
    }
}
