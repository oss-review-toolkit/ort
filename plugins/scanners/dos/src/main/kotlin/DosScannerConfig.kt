/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.scanners.dos

import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.plugins.api.Secret

/**
 * This is the configuration class for DOS Scanner.
 */
data class DosScannerConfig(
    /** The URL where the DOS backend is running. */
    val url: String,

    /** The secret token to use with the DOS backend. */
    val token: Secret,

    /** The timeout for communicating with the DOS backend, in seconds. */
    val timeout: Long?,

    /** Interval (in seconds) to use for polling scanjob status from DOS API. **/
    @OrtPluginOption(defaultValue = "5")
    val pollInterval: Long,

    /** The URL where the DOS / package curation front-end is running. **/
    @OrtPluginOption(defaultValue = "http://localhost:3000")
    val frontendUrl: String,

    /**
     * Whether to write scan results to the storage.
     */
    @OrtPluginOption(defaultValue = "true")
    val writeToStorage: Boolean
)
