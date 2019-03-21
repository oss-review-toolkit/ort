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

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * A repository specific configuration for ORT. This configuration is parsed from a ".ort.yml" file in the input
 * directory of the analyzer. It will be included in the analyzer result and can be further processed by the other
 * tools.
 */
data class RepositoryConfiguration(
    /**
     * Defines which parts of the repository will be excluded. Note that excluded parts will still be analyzed and
     * scanned, but related errors will be marked as resolved in the reporter output.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val excludes: Excludes? = null,

    /**
     * Defines resolutions for issues with this repository.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val resolutions: Resolutions? = null
)
