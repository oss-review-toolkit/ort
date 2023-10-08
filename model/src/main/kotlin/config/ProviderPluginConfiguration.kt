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

import com.fasterxml.jackson.annotation.JsonIgnore

import com.sksamuel.hoplite.ConfigAlias

import org.ossreviewtoolkit.utils.common.Options
import org.ossreviewtoolkit.utils.common.Plugin

/**
 * The configuration of provider plugins.
 */
data class ProviderPluginConfiguration(
    /**
     * The [type][Plugin.type] of the provider.
     */
    @ConfigAlias("name")
    val type: String,

    /**
     * A unique identifier for the provider.
     */
    val id: String = type,

    /**
     * Whether this provider is enabled.
     */
    val enabled: Boolean = true,

    /**
     * The configuration options of the provider. See the specific implementation for available configuration options.
     */
    @ConfigAlias("config")
    val options: Options = emptyMap(),

    /**
     * The configuration secrets of the provider. See the specific implementation for available secret options.
     *
     * This property is not serialized to ensure that secrets do not appear in serialized output.
     */
    @JsonIgnore
    val secrets: Options = emptyMap()
) {
    override fun toString(): String {
        // Do not use the generated toString function for the data class to ensure that the output does not contain the
        // secret options.
        return "${this::class.simpleName}(type=$type, id=$id, enabled=$enabled, options=$options)"
    }
}
