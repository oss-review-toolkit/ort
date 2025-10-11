/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.scanners.fossologynomossa

import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.scanner.ScannerMatcherCriteria

/**
 * Configuration options for the Nomossa scanner.
 */
data class NomossaConfig(
    /**
     * Command line options that affect scan results. These are used when matching stored results.
     */
    @OrtPluginOption(defaultValue = "-J,-S,-l")
    val additionalOptions: List<String>,

    /**
     * The scanner name pattern used when looking up scan results from storage.
     */
    override val regScannerName: String?,

    /**
     * The minimum version of scan results to use from storage.
     */
    override val minVersion: String?,

    /**
     * The maximum version of scan results to use from storage.
     */
    override val maxVersion: String?,

    /**
     * The configuration string for identifying matching scan results in storage.
     */
    override val configuration: String?,

    /**
     * Whether to read scan results from storage.
     */
    @OrtPluginOption(defaultValue = "false")
    val readFromStorage: Boolean,

    /**
     * Whether to write scan results to storage.
     */
    @OrtPluginOption(defaultValue = "false")
    val writeToStorage: Boolean
) : ScannerMatcherCriteria
