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

import java.util.ServiceLoader

import org.ossreviewtoolkit.utils.common.Options

/**
 * A factory interface for creating plugins of type [PLUGIN]. The different plugin endpoints ORT provides must inherit
 * from this interface.
 */
@Suppress("TooManyFunctions")
interface PluginFactory<out PLUGIN : Plugin> {
    companion object {
        /**
         * Return all plugin factories of type [FACTORY].
         */
        inline fun <reified FACTORY : PluginFactory<PLUGIN>, PLUGIN : Plugin> getAll() =
            getLoaderFor<FACTORY>()
                .iterator()
                .asSequence()
                .associateByTo(sortedMapOf(String.CASE_INSENSITIVE_ORDER)) {
                    it.descriptor.id
                }
    }

    /**
     * The descriptor of the plugin
     */
    val descriptor: PluginDescriptor

    /**
     * Create a new instance of [PLUGIN] from [config].
     */
    fun create(config: PluginConfig): PLUGIN

    private inline fun <T> parseNullableOption(
        name: String,
        options: Options,
        expectedType: PluginOptionType,
        convert: (String) -> T
    ): T? {
        val option = descriptor.options.single { it.name == name }

        require(option.type == expectedType) {
            "Option '$name' of plugin '${descriptor.id}' is not of type $expectedType."
        }

        buildList {
            add(option.name)
            addAll(option.aliases)
        }.forEach {
            options[it]?.let { value -> return convert(value) }
        }

        option.defaultValue?.let { return convert(it) }

        return null
    }

    private inline fun <T> parseOption(
        name: String,
        options: Options,
        expectedType: PluginOptionType,
        convert: (String) -> T
    ): T =
        checkNotNull(parseNullableOption(name, options, expectedType, convert)) {
            "No value found for required option '$name' of plugin '${descriptor.id}'."
        }

    /** Parse a [boolean][PluginOptionType.BOOLEAN] option from the given [config]. */
    fun parseBooleanOption(name: String, config: PluginConfig): Boolean =
        parseOption(name, config.options, PluginOptionType.BOOLEAN) { it.toBooleanStrict() }

    /** Parse a nullable [boolean][PluginOptionType.BOOLEAN] option from the given [config]. */
    fun parseNullableBooleanOption(name: String, config: PluginConfig): Boolean? =
        parseNullableOption(name, config.options, PluginOptionType.BOOLEAN) { it.toBooleanStrict() }

    /** Parse an [integer][PluginOptionType.INTEGER] option from the given [config]. */
    fun parseIntegerOption(name: String, config: PluginConfig): Int =
        parseOption(name, config.options, PluginOptionType.INTEGER) { it.toInt() }

    /** Parse a nullable [integer][PluginOptionType.INTEGER] option from the given [config]. */
    fun parseNullableIntegerOption(name: String, config: PluginConfig): Int? =
        parseNullableOption(name, config.options, PluginOptionType.INTEGER) { it.toInt() }

    /** Parse a [long][PluginOptionType.LONG] option from the given [config]. */
    fun parseLongOption(name: String, config: PluginConfig): Long =
        parseOption(name, config.options, PluginOptionType.LONG) { it.toLong() }

    /** Parse a nullable [long][PluginOptionType.LONG] option from the given [config]. */
    fun parseNullableLongOption(name: String, config: PluginConfig): Long? =
        parseNullableOption(name, config.options, PluginOptionType.LONG) { it.toLong() }

    /** Parse a [secret][PluginOptionType.SECRET] option from the given [config]. */
    fun parseSecretOption(name: String, config: PluginConfig): Secret =
        parseOption(name, config.secrets, PluginOptionType.SECRET) { Secret(it) }

    /** Parse a nullable [secret][PluginOptionType.SECRET] option from the given [config]. */
    fun parseNullableSecretOption(name: String, config: PluginConfig): Secret? =
        parseNullableOption(name, config.secrets, PluginOptionType.SECRET) { Secret(it) }

    /** Parse a [string][PluginOptionType.STRING] option from the given [config]. */
    fun parseStringOption(name: String, config: PluginConfig): String =
        parseOption(name, config.options, PluginOptionType.STRING) { it }

    /** Parse a nullable [string][PluginOptionType.STRING] option from the given [config]. */
    fun parseNullableStringOption(name: String, config: PluginConfig): String? =
        parseNullableOption(name, config.options, PluginOptionType.STRING) { it }

    /** Parse a [string list][PluginOptionType.STRING_LIST] option from the given [config]. */
    fun parseStringListOption(name: String, config: PluginConfig): List<String> =
        parseOption(name, config.options, PluginOptionType.STRING_LIST) { value ->
            value.split(',').mapNotNull { it.trim().ifEmpty { null } }
        }

    /** Parse a nullable [string list][PluginOptionType.STRING_LIST] option from the given [config]. */
    fun parseNullableStringListOption(name: String, config: PluginConfig): List<String>? =
        parseNullableOption(name, config.options, PluginOptionType.STRING_LIST) { value ->
            value.split(',').mapNotNull { it.trim().ifEmpty { null } }
        }
}

/**
 * A plugin that ORT can use. Each plugin extension point of ORT must inherit from this interface.
 */
interface Plugin {
    val descriptor: PluginDescriptor
}

/**
 * Return a [ServiceLoader] that is capable of loading services of type [T].
 */
inline fun <reified T : Any> getLoaderFor(): ServiceLoader<T> = ServiceLoader.load(T::class.java)
