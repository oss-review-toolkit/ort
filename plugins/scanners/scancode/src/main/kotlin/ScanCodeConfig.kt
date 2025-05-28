/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.scanners.scancode

import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.scanner.ScanStorage
import org.ossreviewtoolkit.scanner.ScannerMatcherCriteria

data class ScanCodeConfig(
    /**
     * Command line options that modify the result. These are added to the [ScannerDetails] when looking up results from
     * a [ScanStorage].
     */
    @OrtPluginOption(defaultValue = "--copyright,--license,--info,--strip-root,--timeout,300")
    val commandLine: List<String>,

    /**
     * Command line options that do not modify the result and should therefore not be considered in [configuration],
     * like "--processes". If this does not contain "--processes", it is added with a value of one less than the number
     * of available processors.
     */
    @OrtPluginOption(defaultValue = "")
    val commandLineNonConfig: List<String>,

    /**
     * A flag to indicate whether the "high-level" per-file license reported by ScanCode starting with version 32 should
     * be used instead of the individual "low-level" per-line license findings. The per-file license may be different
     * from the conjunction of per-line licenses and is supposed to contain fewer false-positives. However, no exact
     * line numbers can be associated to the per-file license anymore. If enabled, the start line of the per-file
     * license finding is set to the minimum of all start lines for per-line findings in that file, the end line is set
     * to the maximum of all end lines for per-line findings in that file, and the score is set to the arithmetic
     * average of the scores of all per-line findings in that file.
     */
    @OrtPluginOption(defaultValue = "false")
    val preferFileLicense: Boolean,

    /**
     * A regular expression to match the scanner name when looking up scan results in the storage.
     */
    override val regScannerName: String?,

    /**
     * The minimum version of stored scan results to use.
     */
    override val minVersion: String?,

    /**
     * The maximum version of stored scan results to use.
     */
    override val maxVersion: String?,

    /**
     * The configuration to use for the scanner. Only scan results with the same configuration are used when looking up
     * scan results in the storage.
     */
    override val configuration: String?,

    /**
     * Whether to read scan results from the storage.
     */
    @OrtPluginOption(defaultValue = "true")
    val readFromStorage: Boolean,

    /**
     * Whether to write scan results to the storage.
     */
    @OrtPluginOption(defaultValue = "true")
    val writeToStorage: Boolean
) : ScannerMatcherCriteria
