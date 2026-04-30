/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.advisors.scanoss

import org.ossreviewtoolkit.clients.scanoss.SCANOSS_BASE_URL
import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.plugins.api.Secret

/**
 * The configuration for the SCANOSS vulnerability provider.
 */
data class ScanOssConfig(
    /** The URL of the SCANOSS server. */
    @OrtPluginOption(defaultValue = SCANOSS_BASE_URL)
    val apiUrl: String,

    /** The API key used to authenticate with the SCANOSS server. */
    val apiKey: Secret
)
