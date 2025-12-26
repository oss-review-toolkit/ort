/*
 * Copyright (C) 2024 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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
enum class PluginOptionType(val typeName: String) {
    /** A [Boolean] option. */
    BOOLEAN("Boolean"),

    /** An enum option. */
    ENUM("Enum"),

    /** An enum [List] option. */
    ENUM_LIST("EnumList"),

    /** An [Int] option. */
    INTEGER("Integer"),

    /** A [Long] option. */
    LONG("Long"),

    /** A [Secret] option. */
    SECRET("Secret"),

    /** A [String] option. */
    STRING("String"),

    /** A [List]<[String]> option. */
    STRING_LIST("StringList");

    override fun toString() = typeName
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
     * The enum type name if [type] is [PluginOptionType.ENUM] or [PluginOptionType.ENUM_LIST], `null` otherwise.
     */
    val enumType: String?,

    /**
     * The enum entries if [type] is [PluginOptionType.ENUM] or [PluginOptionType.ENUM_LIST], `null` otherwise.
     */
    val enumEntries: List<EnumEntry>?,

    /**
     * The default value of the option, or `null` if the option is required.
     */
    val defaultValue: String?,

    /**
     * A list of alternative names for the option.
     */
    val aliases: List<String>,

    /**
     * Whether the option is nullable.
     */
    val isNullable: Boolean,

    /**
     * Whether the option is required.
     */
    val isRequired: Boolean
)

/** A description of an enum entry. */
data class EnumEntry(
    /** The name of the enum entry. */
    val name: String,

    /** An alternative name for the enum entry, used instead of [name] when reading config maps. */
    val alternativeName: String?,

    /** A list of alternative names for the enum entry. */
    val aliases: List<String>
)

/**
 * A secret value that should not be printed in logs.
 */
@JvmInline
value class Secret(val value: String) {
    override fun toString() = "***"
}
