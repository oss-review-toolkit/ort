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

import java.time.Instant

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.utils.ort.Environment

/**
 * This class calculates [Metadata] for a given [OrtResult].
 */
internal class MetadataCalculator {
    fun getMetadata(ortResult: OrtResult) =
        Metadata(
            analyzerStartTime = ortResult.analyzer?.startTime ?: Instant.now(),
            analyzerEndTime = ortResult.analyzer?.endTime ?: Instant.now(),
            analyzerEnvironment = ortResult.analyzer?.environment ?: Environment(),
            scannerStartTime = ortResult.scanner?.startTime ?: Instant.now(),
            scannerEndTime = ortResult.scanner?.endTime ?: Instant.now(),
            scannerEnvironment = ortResult.scanner?.environment ?: Environment(),
            evaluatorStartTime = ortResult.evaluator?.startTime ?: Instant.now(),
            evaluatorEndTime = ortResult.evaluator?.endTime ?: Instant.now(),
            evaluatorEnvironment = ortResult.advisor?.environment ?: Environment(),
            advisorStartTime = ortResult.advisor?.startTime ?: Instant.now(),
            advisorEndTime = ortResult.advisor?.endTime ?: Instant.now(),
            advisorEnvironment = ortResult.advisor?.environment ?: Environment()
        )
}
