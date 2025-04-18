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

package org.ossreviewtoolkit.plugins.compiler

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier

import com.squareup.kotlinpoet.ksp.toTypeName

import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.api.PluginOption
import org.ossreviewtoolkit.plugins.api.PluginOptionType

/**
 * A generator for [PluginSpec] instances.
 */
class PluginSpecFactory {
    /**
     * Create a [PluginSpec] for the given [ortPlugin] using the [pluginClass] and [pluginFactoryClass]. The
     * [pluginBaseClass] is used to derive the plugin ID if none is provided as part of [ortPlugin].
     */
    fun create(
        ortPlugin: OrtPlugin,
        pluginClass: KSClassDeclaration,
        pluginBaseClass: KSClassDeclaration,
        pluginFactoryClass: KSClassDeclaration
    ): PluginSpec {
        val pluginType = pluginClass.asType(emptyList()).toTypeName()
        val pluginFactoryType = pluginFactoryClass.asType(emptyList()).toTypeName()

        val constructor = getPluginConstructor(pluginClass)
        val (configClass, configType) = if (constructor.parameters.size == 2) {
            val type = constructor.parameters[1].type
            type.resolve().declaration as KSClassDeclaration to type.toTypeName()
        } else {
            null to null
        }

        val pluginOptions = configClass?.getPluginOptions().orEmpty()

        val pluginId = ortPlugin.id.ifEmpty {
            derivePluginId(pluginClass.simpleName.asString(), pluginBaseClass.simpleName.asString())
        }

        return PluginSpec(
            containingFile = pluginClass.containingFile,
            descriptor = PluginDescriptor(
                id = pluginId,
                displayName = ortPlugin.displayName,
                description = ortPlugin.description,
                options = pluginOptions
            ),
            packageName = pluginClass.packageName.asString(),
            typeName = pluginType,
            configClass = configType?.let { PluginConfigClassSpec(it) },
            factory = PluginFactorySpec(pluginFactoryType, pluginFactoryClass.qualifiedName?.asString().orEmpty())
        )
    }

    /**
     * Get the constructor of the plugin class that has a [PluginDescriptor] and a config argument. Throw an
     * [IllegalArgumentException] if more than one or no such constructor exists.
     */
    private fun getPluginConstructor(pluginClass: KSClassDeclaration): KSFunctionDeclaration {
        // TODO: Consider adding an @OrtPluginConstructor annotation to mark the constructor to use. This could be
        //       useful if a plugin needs multiple constructors for different purposes like testing.
        val constructors = pluginClass.getConstructors().filterTo(mutableListOf()) {
            if (it.parameters.size < 1 || it.parameters.size > 2) {
                return@filterTo false
            }

            val firstArgumentIsDescriptor = it.parameters[0].name?.asString() == "descriptor" &&
                it.parameters[0].type.resolve().declaration.qualifiedName?.asString() ==
                "org.ossreviewtoolkit.plugins.api.PluginDescriptor"

            val optionalSecondArgumentIsCalledConfig =
                it.parameters.size == 1 || it.parameters[1].name?.asString() == "config"

            firstArgumentIsDescriptor && optionalSecondArgumentIsCalledConfig
        }

        return requireNotNull(constructors.singleOrNull()) {
            "Plugin class $pluginClass must have exactly one constructor with a PluginDescriptor and an optional " +
                "config argument."
        }
    }

    /**
     * Get the plugin options from the config class by mapping its properties to [PluginOption] instances.
     */
    @OptIn(KspExperimental::class)
    private fun KSClassDeclaration.getPluginOptions(): List<PluginOption> {
        require(Modifier.DATA in modifiers) {
            "Config class $this must be a data class."
        }

        val constructor = requireNotNull(getConstructors().singleOrNull()) {
            "Config class $this must have exactly one constructor."
        }

        return constructor.parameters.map { param ->
            val paramType = param.type.resolve()
            val paramTypeString = getQualifiedNameWithTypeArguments(paramType)
            val paramName = param.name?.asString()

            requireNotNull(paramName) {
                "Config class constructor parameter has no name."
            }

            require(param.isVal) {
                "Config class constructor parameter $paramName must be a val."
            }

            require(!param.hasDefault) {
                "Config class constructor parameter $paramName must not have a default value. Default values must be " +
                    "set via the @OrtPluginOption annotation."
            }

            val prop = getAllProperties().find { it.simpleName.asString() == paramName }

            requireNotNull(prop) {
                "Config class must have a property with the name $paramName."
            }

            val annotations = prop.getAnnotationsByType(OrtPluginOption::class).toList()

            require(annotations.size <= 1) {
                "Config class constructor parameter $paramName must have at most one @OrtPluginOption annotation."
            }

            val annotation = annotations.firstOrNull()

            val type = when (paramTypeString) {
                "kotlin.Boolean" -> PluginOptionType.BOOLEAN
                "kotlin.Int" -> PluginOptionType.INTEGER
                "kotlin.Long" -> PluginOptionType.LONG
                "org.ossreviewtoolkit.plugins.api.Secret" -> PluginOptionType.SECRET
                "kotlin.String" -> PluginOptionType.STRING
                "kotlin.collections.List<kotlin.String>" -> PluginOptionType.STRING_LIST

                else -> throw IllegalArgumentException(
                    "Config class constructor parameter ${param.name?.asString()} has unsupported type " +
                        "$paramTypeString."
                )
            }

            val defaultValue = annotation?.defaultValue?.takeUnless { it == OrtPluginOption.NO_DEFAULT_VALUE }

            PluginOption(
                name = param.name?.asString().orEmpty(),
                description = prop.docString?.trim().orEmpty(),
                type = type,
                defaultValue = defaultValue,
                aliases = annotation?.aliases?.asList().orEmpty(),
                isNullable = paramType.isMarkedNullable,
                isRequired = !paramType.isMarkedNullable && defaultValue == null
            )
        }
    }

    /**
     * Get the qualified name of a [type] with its type arguments, for example,
     * `kotlin.collections.List<kotlin.String>`.
     */
    private fun getQualifiedNameWithTypeArguments(type: KSType): String =
        buildString {
            append(type.declaration.qualifiedName?.asString())
            if (type.arguments.isNotEmpty()) {
                append("<")
                append(
                    type.arguments.joinToString(", ") { argument ->
                        argument.type?.resolve()?.let { getQualifiedNameWithTypeArguments(it) } ?: "Unknown"
                    }
                )
                append(">")
            }
        }
}

internal fun derivePluginId(pluginClassName: String, pluginBaseClassName: String): String =
    pluginClassName.removeSuffix(pluginBaseClassName.removePrefix("Ort"))
