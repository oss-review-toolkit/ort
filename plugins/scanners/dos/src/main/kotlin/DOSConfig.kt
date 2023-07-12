/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.model.config.ScannerConfiguration

internal data class DOSConfig(
    /** The URL where the DOS service is running. */
    val serverUrl: String,

    /** Interval (in seconds) to use for polling scanjob status from DOS API. */
    val pollInterval: Int
) {
    companion object: Logging {
        /** Name of the configuration property for the server URL. */
        private const val SERVER_URL_PROPERTY = "serverUrl"

        /** Name of the configuration property for the polling interval. */
        private const val POLLING_INTERVAL_PROPERTY = "pollInterval"

        private const val DEFAULT_SERVER_URL = "https://default.value.com"

        private const val DEFAULT_POLLING_INTERVAL = 5

        fun create(scannerConfig: ScannerConfiguration): DOSConfig {
            val dosScannerOptions = scannerConfig.options?.get("DOS")

            //requireNotNull(DOSScannerOptions) { "No DOS Scanner configuration found." }

            val serverUrl = dosScannerOptions?.get(SERVER_URL_PROPERTY) ?: DEFAULT_SERVER_URL
            val pollInterval = dosScannerOptions?.get(POLLING_INTERVAL_PROPERTY)?.toInt() ?: DEFAULT_POLLING_INTERVAL

            require(pollInterval >= DEFAULT_POLLING_INTERVAL) {
                "Polling interval must be >= $DEFAULT_POLLING_INTERVAL, current value is $pollInterval"
            }
            return DOSConfig(
                serverUrl,
                pollInterval
            )
        }
    }
}
