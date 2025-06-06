/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.model.config

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonInclude

import com.sksamuel.hoplite.ConfigAlias

import org.ossreviewtoolkit.plugins.api.PluginConfig
import org.ossreviewtoolkit.plugins.api.PluginDescriptor

/**
 * The configuration model of the advisor. This class is (de-)serialized in the following places:
 * - Deserialized from "config.yml" as part of [OrtConfiguration] (via Hoplite).
 * - (De-)Serialized as part of [org.ossreviewtoolkit.model.OrtResult] (via Jackson).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class AdvisorConfiguration(
    /**
     * A flag to control whether excluded scopes and paths should be skipped when giving the advice.
     */
    val skipExcluded: Boolean = false,

    /**
     * A map with [configuration][PluginConfig] for advice providers using the [plugin id][PluginDescriptor.id] as key.
     */
    @ConfigAlias("config")
    @JsonAlias("config")
    val advisors: Map<String, PluginConfig>? = null
)
