/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.scanners.scanoss

import com.scanoss.rest.ScanApi

import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.plugins.api.Secret

data class ScanOssConfig(
    /** The URL of the ScanOSS server. */
    @OrtPluginOption(defaultValue = ScanApi.DEFAULT_BASE_URL)
    val apiUrl: String,

    /** The API key used to authenticate with the ScanOSS server. */
    @OrtPluginOption(defaultValue = "")
    val apiKey: Secret,

    /**
     * A regular expression to match the scanner name when looking up scan results in the storage.
     */
    val regScannerName: String?,

    /**
     * The minimum version of stored scan results to use.
     */
    val minVersion: String?,

    /**
     * The maximum version of stored scan results to use.
     */
    val maxVersion: String?,

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
)
