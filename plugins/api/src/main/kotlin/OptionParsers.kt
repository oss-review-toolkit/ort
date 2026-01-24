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

@file:Suppress("TooManyFunctions")

package org.ossreviewtoolkit.plugins.api

import org.ossreviewtoolkit.utils.common.Options

private inline fun <T> PluginFactory<*>.parseNullableOption(
    name: String,
    options: Options,
    expectedType: PluginOptionType,
    convert: (option: PluginOption, value: String) -> T
): T? {
    val option = descriptor.options.single { it.name == name }

    require(option.type == expectedType) {
        "Option '$name' of plugin '${descriptor.id}' is not of type $expectedType."
    }

    buildList {
        add(option.name)
        addAll(option.aliases)
    }.forEach {
        options[it]?.let { value -> return convert(option, value) }
    }

    option.defaultValue?.let { return convert(option, it) }

    return null
}

private inline fun <T> PluginFactory<*>.parseOption(
    name: String,
    options: Options,
    expectedType: PluginOptionType,
    convert: (option: PluginOption, value: String) -> T
): T =
    checkNotNull(parseNullableOption(name, options, expectedType, convert)) {
        "No value found for required option '$name' of plugin '${descriptor.id}'."
    }

/** Parse a [boolean][PluginOptionType.BOOLEAN] option from the given [config]. */
fun PluginFactory<*>.parseBooleanOption(name: String, config: PluginConfig): Boolean =
    parseOption(name, config.options, PluginOptionType.BOOLEAN) { _, value -> value.toBooleanStrict() }

/** Parse a nullable [boolean][PluginOptionType.BOOLEAN] option from the given [config]. */
fun PluginFactory<*>.parseNullableBooleanOption(name: String, config: PluginConfig): Boolean? =
    parseNullableOption(name, config.options, PluginOptionType.BOOLEAN) { _, value -> value.toBooleanStrict() }

/** Parse an [integer][PluginOptionType.INTEGER] option from the given [config]. */
fun PluginFactory<*>.parseIntegerOption(name: String, config: PluginConfig): Int =
    parseOption(name, config.options, PluginOptionType.INTEGER) { _, value -> value.toInt() }

/** Parse a nullable [integer][PluginOptionType.INTEGER] option from the given [config]. */
fun PluginFactory<*>.parseNullableIntegerOption(name: String, config: PluginConfig): Int? =
    parseNullableOption(name, config.options, PluginOptionType.INTEGER) { _, value -> value.toInt() }

/** Parse a [long][PluginOptionType.LONG] option from the given [config]. */
fun PluginFactory<*>.parseLongOption(name: String, config: PluginConfig): Long =
    parseOption(name, config.options, PluginOptionType.LONG) { _, value -> value.toLong() }

/** Parse a nullable [long][PluginOptionType.LONG] option from the given [config]. */
fun PluginFactory<*>.parseNullableLongOption(name: String, config: PluginConfig): Long? =
    parseNullableOption(name, config.options, PluginOptionType.LONG) { _, value -> value.toLong() }

/** Parse a [secret][PluginOptionType.SECRET] option from the given [config]. */
fun PluginFactory<*>.parseSecretOption(name: String, config: PluginConfig): Secret =
    parseOption(name, config.secrets, PluginOptionType.SECRET) { _, value -> Secret(value) }

/** Parse a nullable [secret][PluginOptionType.SECRET] option from the given [config]. */
fun PluginFactory<*>.parseNullableSecretOption(name: String, config: PluginConfig): Secret? =
    parseNullableOption(name, config.secrets, PluginOptionType.SECRET) { _, value -> Secret(value) }

/** Parse a [string][PluginOptionType.STRING] option from the given [config]. */
fun PluginFactory<*>.parseStringOption(name: String, config: PluginConfig): String =
    parseOption(name, config.options, PluginOptionType.STRING) { _, value -> value }

/** Parse a nullable [string][PluginOptionType.STRING] option from the given [config]. */
fun PluginFactory<*>.parseNullableStringOption(name: String, config: PluginConfig): String? =
    parseNullableOption(name, config.options, PluginOptionType.STRING) { _, value -> value }

/** Parse a [string list][PluginOptionType.STRING_LIST] option from the given [config]. */
fun PluginFactory<*>.parseStringListOption(name: String, config: PluginConfig): List<String> =
    parseOption(name, config.options, PluginOptionType.STRING_LIST) { _, value ->
        value.split(',').mapNotNull { it.trim().ifEmpty { null } }
    }

/** Parse a nullable [string list][PluginOptionType.STRING_LIST] option from the given [config]. */
fun PluginFactory<*>.parseNullableStringListOption(name: String, config: PluginConfig): List<String>? =
    parseNullableOption(name, config.options, PluginOptionType.STRING_LIST) { _, value ->
        value.split(',').mapNotNull { it.trim().ifEmpty { null } }
    }
