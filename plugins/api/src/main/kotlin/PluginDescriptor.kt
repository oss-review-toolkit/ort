/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.api

/**
 * A descriptor holding the metadata of a plugin.
 */
data class PluginDescriptor(
    /**
     * The id of the plugin class. Must be unique among all plugins for the same factory class.
     */
    val id: String,

    /**
     * The display name of the plugin.
     */
    val displayName: String,

    /**
     * The description of the plugin.
     */
    val description: String,

    /**
     * The configuration options supported by the plugin.
     */
    val options: List<PluginOption> = emptyList()
)

/**
 * The supported types of plugin options.
 */
enum class PluginOptionType {
    /** A [Boolean] option. */
    BOOLEAN,

    /** An [Int] option. */
    INTEGER,

    /** A [Long] option. */
    LONG,

    /** A [Secret] option. */
    SECRET,

    /** A [String] option. */
    STRING,

    /** A [List]<[String]> option. */
    STRING_LIST
}

/**
 * A configuration option for a plugin.
 */
data class PluginOption(
    /**
     * The name of the option. Must be unique among all options of the same plugin.
     */
    val name: String,

    /**
     * The description of the option.
     */
    val description: String,

    /**
     * The [type][PluginOptionType] of the option.
     */
    val type: PluginOptionType,

    /**
     * The default value of the option, or `null` if the option is required.
     */
    val defaultValue: String?,

    /**
     * A list of alternative names for the option.
     */
    val aliases: List<String>,

    /**
     * Whether the option is required.
     */
    val isRequired: Boolean
)

/**
 * A secret value that should not be printed in logs.
 */
@JvmInline
value class Secret(val value: String) {
    override fun toString() = "***"
}
