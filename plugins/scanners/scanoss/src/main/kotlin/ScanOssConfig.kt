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
    /** The URL of the SCANOSS server. */
    @OrtPluginOption(defaultValue = ScanApi.DEFAULT_BASE_URL)
    val apiUrl: String,

    /** The API key used to authenticate with the SCANOSS server. */
    @OrtPluginOption(defaultValue = "")
    val apiKey: Secret,

    /**
     * Whether to write scan results to the storage.
     */
    @OrtPluginOption(defaultValue = "true")
    val writeToStorage: Boolean,

    /**
     * Whether to enable path obfuscation when sending file paths to the SCANOSS server.
     * When enabled, the actual file paths will be obfuscated in the requests to protect sensitive information.
     */
    @OrtPluginOption(defaultValue = "false")
    val enablePathObfuscation: Boolean
)
