/*
 * Copyright (C) 2023 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.advisors.ossindex

import org.ossreviewtoolkit.clients.ossindex.OssIndexService
import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.plugins.api.Secret

/**
 * The configuration for the OSS Index provider. Authentication can either happen via OSS Index username and password or
 * Sonatype Guide personal access token.
 */
data class OssIndexConfiguration(
    /**
     * The base URL of the OSS Index REST API.
     */
    @OrtPluginOption(defaultValue = OssIndexService.DEFAULT_BASE_URL)
    val serverUrl: String,

    /**
     * The optional username is null when using Sonatype Guide authentication, or the username when using OSS Index
     * authentication.
     */
    val username: String?,

    /**
     * The personal access token when using Sonatype Guide authentication, or the password when using OSS Index
     * authentication.
     */
    val token: Secret
)
