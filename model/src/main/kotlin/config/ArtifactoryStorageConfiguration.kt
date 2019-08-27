/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package com.here.ort.model.config

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * A class to hold the configuration for using Artifactory as a storage.
 */
data class ArtifactoryStorageConfiguration(
    /**
     * The URL of the Artifactory server, e.g. "https://example.com/artifactory".
     */
    val url: String,

    /**
     * The name of the Artifactory repository to use as a storage.
     */
    val repository: String,

    /**
     * An Artifactory API token with read/write access to [repository].
     */
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    val apiToken: String = ""
)
