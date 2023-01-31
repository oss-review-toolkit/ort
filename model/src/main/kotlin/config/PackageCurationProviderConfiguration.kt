/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import com.sksamuel.hoplite.ConfigAlias

import org.ossreviewtoolkit.utils.common.Plugin

data class PackageCurationProviderConfiguration(
    /**
     * The [type][Plugin.type] of the package curation provider.
     */
    @ConfigAlias("name")
    val type: String,

    /**
     * A unique identifier for the package curation provider.
     */
    val id: String = type,

    /**
     * Whether this curation provider is enabled.
     */
    val enabled: Boolean = true,

    /**
     * The configuration of the package curation provider. See the specific implementation for available configuration
     * options.
     */
    val config: Map<String, String> = emptyMap()
)
