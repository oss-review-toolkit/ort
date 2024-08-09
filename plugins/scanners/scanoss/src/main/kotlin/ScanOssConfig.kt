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

import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.utils.common.Options

/**
 * A data class that holds the configuration options supported by the [ScanOss] scanner. An instance of this class is
 * created from the [scanner config][ScannerConfiguration.config] object under the key _ScanOss_. It offers the
 * following configuration options:
 *
 * **"options.apiUrl":** The URL of the ScanOSS server.
 * **"secrets.apiKey":** The API key to authenticate with the ScanOSS server.
 */
data class ScanOssConfig(
    /** URL of the ScanOSS server. */
    val apiUrl: String,

    /** API Key required to authenticate with the ScanOSS server. */
    val apiKey: String
) {
    companion object {
        /** Name of the configuration property for the API URL. */
        const val API_URL_PROPERTY = "apiUrl"

        /** Name of the configuration property for the API key. */
        const val API_KEY_PROPERTY = "apiKey"

        fun create(options: Options, secrets: Options): ScanOssConfig {
            // TODO: Remove the hard-coded default URL once https://github.com/scanoss/scanoss.java/issues/11 is
            //       resolved.
            val apiUrl = options[API_URL_PROPERTY] ?: "https://api.osskb.org/"
            val apiKey = secrets[API_KEY_PROPERTY].orEmpty()

            return ScanOssConfig(apiUrl, apiKey)
        }
    }
}
