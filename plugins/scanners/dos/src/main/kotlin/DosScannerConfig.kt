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

import org.ossreviewtoolkit.utils.common.Options

/**
 * This is the configuration class for DOS Scanner.
 */
data class DosScannerConfig(
    /** The URL where the DOS backend is running. */
    val url: String,

    /** The secret token to use with the DOS backend. */
    val token: String,

    /** The timeout for communicating with the DOS backend, in seconds. */
    val timeout: Long?,

    /** Interval (in seconds) to use for polling scanjob status from DOS API. **/
    val pollInterval: Long,

    /** Use license conclusions as detected licenses when they exist? **/
    val fetchConcluded: Boolean,

    /** The URL where the DOS / package curation front-end is running. **/
    val frontendUrl: String
) {
    companion object {
        private const val DEFAULT_FRONT_END_URL = "http://localhost:3000"
        private const val DEFAULT_POLLING_INTERVAL = 5L

        fun create(options: Options, secrets: Options): DosScannerConfig {
            return DosScannerConfig(
                url = options.getValue("url"),
                token = secrets.getValue("token"),
                timeout = options["timeout"]?.toLongOrNull(),
                pollInterval = options["pollInterval"]?.toLongOrNull() ?: DEFAULT_POLLING_INTERVAL,
                fetchConcluded = options["fetchConcluded"].toBoolean(),
                frontendUrl = options["frontendUrl"] ?: DEFAULT_FRONT_END_URL
            )
        }
    }
}
