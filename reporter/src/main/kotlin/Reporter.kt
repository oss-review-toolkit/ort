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

package com.here.ort.reporter

import com.here.ort.model.AnalyzerResult
import com.here.ort.model.OrtResult
import com.here.ort.model.ScanRecord
import com.here.ort.model.config.CopyrightGarbage

import java.io.OutputStream
import java.util.ServiceLoader

/**
 * A reporter that creates a human readable report from the [AnalyzerResult] and [ScanRecord] contained in an
 * [OrtResult]. The signatures of public functions in this class define the library API.
 */
abstract class Reporter {
    companion object {
        private val LOADER = ServiceLoader.load(Reporter::class.java)!!

        /**
         * The list of all available reporters in the classpath.
         */
        val ALL by lazy { LOADER.iterator().asSequence().toList() }
    }

    /**
     * The name to use to refer to the reporter.
     */
    abstract val reporterName: String

    /**
     * The default output filename to use with this reporter format.
     */
    abstract val defaultFilename: String

    /**
     * Generate a report for the [ortResult], taking into account any issue resolutions provided by [resolutionProvider]
     * and the given known [copyrightGarbage]. The report may be post-processed by a [postProcessingScript] before it is
     * written to [outputStream].
     */
    abstract fun generateReport(
        ortResult: OrtResult,
        resolutionProvider: ResolutionProvider,
        copyrightGarbage: CopyrightGarbage,
        outputStream: OutputStream,
        postProcessingScript: String? = null
    )
}
