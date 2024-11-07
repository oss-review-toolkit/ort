/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.reporter

import java.io.File

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.plugins.api.Plugin

/**
 * A reporter that creates a human-readable report from a given [OrtResult].
 */
interface Reporter : Plugin {
    /**
     * Generate a report for the provided [input] and write the generated file(s) to the [outputDir]. If and how the
     * [input] data is used depends on the specific reporter implementation.
     * Return a list of [Result]s that wrap the generated report file on success, or a file specific failure if
     * something went wrong.
     */
    fun generateReport(input: ReporterInput, outputDir: File): List<Result<File>>
}
