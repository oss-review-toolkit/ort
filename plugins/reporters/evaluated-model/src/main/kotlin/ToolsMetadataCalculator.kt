/*
 * Copyright (C) 2021 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.reporters.evaluatedmodel

import org.ossreviewtoolkit.model.OrtResult

/**
 * This class calculates [ToolsMetadata] for a given [OrtResult].
 */
internal class ToolsMetadataCalculator {
    fun getMetadata(ortResult: OrtResult) =
        ToolsMetadata(
            analyzer = ortResult.analyzer?.let { run ->
                ToolsMetadata.Run(
                    startTime = run.startTime,
                    endTime = run.endTime,
                    environment = run.environment
                )
            },
            scanner = ortResult.scanner?.let { run ->
                ToolsMetadata.Run(
                    startTime = run.startTime,
                    endTime = run.endTime,
                    environment = run.environment
                )
            },
            advisor = ortResult.advisor?.let { run ->
                ToolsMetadata.Run(
                    startTime = run.startTime,
                    endTime = run.endTime,
                    environment = run.environment
                )
            },
            evaluator = ortResult.evaluator?.let { run ->
                ToolsMetadata.Run(
                    startTime = run.startTime,
                    endTime = run.endTime,
                    environment = run.environment
                )
            }
        )
}
