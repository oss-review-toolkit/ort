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

package org.ossreviewtoolkit.plugins.compiler

import com.google.devtools.ksp.processing.CodeGenerator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.writeTo

import org.ossreviewtoolkit.plugins.api.PluginConfig
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.api.PluginOption
import org.ossreviewtoolkit.plugins.api.PluginOptionType
import org.ossreviewtoolkit.plugins.api.Secret

class PluginFactoryGenerator(private val codeGenerator: CodeGenerator) {
    fun generate(pluginSpec: PluginSpec): ServiceLoaderSpec {
        val generatedFactory = generateFactoryClass(pluginSpec)
        return ServiceLoaderSpec(pluginSpec, generatedFactory)
    }

    /**
     * Generate a factory class for the [pluginSpec].
     */
    private fun generateFactoryClass(pluginSpec: PluginSpec): TypeSpec {
        // Create the initializers for the plugin config object.
        val configFromMapInitializer = pluginSpec.configClass?.let {
            getConfigFromMapInitializer(it.typeName, pluginSpec.descriptor.options)
        }

        val configArguments = pluginSpec.configClass?.let {
            getConfigArguments(pluginSpec.descriptor.options)
        }.orEmpty()

        val configFromArgumentsInitializer = pluginSpec.configClass?.let {
            getConfigFromArgumentsInitializer(it.typeName, pluginSpec.descriptor.options)
        }

        // Create the plugin descriptor property.
        val descriptorInitializer = getDescriptorInitializer(pluginSpec.descriptor)
        val descriptorProperty = PropertySpec.builder("descriptor", PluginDescriptor::class)
            .initializer(descriptorInitializer)
            .build()

        val descriptorDelegateProperty = PropertySpec.builder("descriptor", PluginDescriptor::class, KModifier.OVERRIDE)
            .delegate("Companion::descriptor")
            .build()

        // Create the factory function that initializes the plugin with the descriptor and the config object, created
        // from a PluginOptions object.
        val createFromConfigFunction = FunSpec.builder("create").apply {
            addModifiers(KModifier.OVERRIDE)
            addParameter("config", PluginConfig::class)

            if (configFromMapInitializer != null) {
                addCode(configFromMapInitializer)
                addCode("return %T(%N, configObject)", pluginSpec.typeName, descriptorProperty)
            } else {
                addCode("return %T(%N)", pluginSpec.typeName, descriptorProperty)
            }

            returns(pluginSpec.typeName)
        }.build()

        // Create the factory function that initializes the plugin with the descriptor and the config object, created
        // from function arguments.
        val createFromArgumentsFunction = FunSpec.builder("create").apply {
            configArguments.forEach { addParameter(it) }

            if (configFromArgumentsInitializer != null) {
                addCode(configFromArgumentsInitializer)
                addCode("return %T(%N, configObject)", pluginSpec.typeName, descriptorProperty)
            } else {
                addCode("return %T(%N)", pluginSpec.typeName, descriptorProperty)
            }

            returns(pluginSpec.typeName)
        }.build()

        val companionObject = TypeSpec.companionObjectBuilder()
            .addProperty(descriptorProperty)
            .addFunction(createFromArgumentsFunction)
            .build()

        // Create the factory class.
        val className = "${pluginSpec.typeName.toString().substringAfterLast('.')}Factory"
        val classSpec = TypeSpec.classBuilder(className)
            .addSuperinterface(pluginSpec.factory.typeName)
            .addType(companionObject)
            .addProperty(descriptorDelegateProperty)
            .addFunction(createFromConfigFunction)
            .build()

        // Write the factory class to a file.
        FileSpec.builder(ClassName(pluginSpec.packageName, className))
            .addType(classSpec)
            .build()
            .writeTo(codeGenerator, aggregating = true, originatingKSFiles = listOfNotNull(pluginSpec.containingFile))

        return classSpec
    }

    /**
     * Generate the code block to initialize the config object from the [PluginConfig].
     */
    private fun getConfigFromMapInitializer(configType: TypeName, pluginOptions: List<PluginOption>) =
        CodeBlock.builder().apply {
            add("val configObject = %T(\n", configType)

            pluginOptions.forEach { option ->
                add("    ${option.name} = ")

                val parserFunction = MemberName(
                    "org.ossreviewtoolkit.plugins.api",
                    if (option.isNullable) "parseNullable${option.type}Option" else "parse${option.type}Option"
                )

                add("%M(%S, config),\n", parserFunction, option.name)
            }

            // TODO: Decide if an exception should be thrown if the options or secrets maps contain values that do not
            //       match any plugin option. This would be a safety net to catch typos in option names.

            add(")\n\n")
        }.build()

    private fun getConfigArguments(pluginOptions: List<PluginOption>) =
        pluginOptions.map { option ->
            val type = when (option.type) {
                PluginOptionType.BOOLEAN -> Boolean::class.asClassName()
                PluginOptionType.INTEGER -> Int::class.asClassName()
                PluginOptionType.LONG -> Long::class.asClassName()
                PluginOptionType.SECRET -> Secret::class.asClassName()
                PluginOptionType.STRING -> String::class.asClassName()
                PluginOptionType.STRING_LIST -> List::class.asClassName().parameterizedBy(String::class.asClassName())
            }.copy(nullable = option.isNullable)

            val builder = ParameterSpec.builder(option.name, type)

            option.defaultValue?.let { defaultValue ->
                val codeBlock = CodeBlock.builder().apply {
                    when (option.type) {
                        PluginOptionType.BOOLEAN -> add("%L", defaultValue.toBoolean())
                        PluginOptionType.INTEGER -> add("%L", defaultValue.toInt())
                        PluginOptionType.LONG -> add("%LL", defaultValue.toLong())
                        PluginOptionType.SECRET -> add("%T(%S)", Secret::class, defaultValue)
                        PluginOptionType.STRING -> add("%S", defaultValue)
                        PluginOptionType.STRING_LIST -> {
                            if (defaultValue.isEmpty()) {
                                add("emptyList()")
                            } else {
                                add("listOf(")

                                defaultValue.split(',').forEach { value ->
                                    add("%S,", value.trim())
                                }

                                add(")")
                            }
                        }
                    }
                }.build()

                builder.defaultValue(codeBlock)
            }

            // Default to null for nullable properties even if there is no default value to simplify usage of the
            // function.
            if (option.isNullable && option.defaultValue == null) builder.defaultValue("null")

            builder.build()
        }

    /**
     * Generate the code block to initialize the config object from function arguments.
     */
    private fun getConfigFromArgumentsInitializer(configType: TypeName, pluginOptions: List<PluginOption>) =
        CodeBlock.builder().apply {
            add("val configObject = %T(\n", configType)

            pluginOptions.forEach { option ->
                add("    ${option.name} = ${option.name},\n")
            }

            add(")\n\n")
        }.build()

    /**
     * Generate the code block to initialize the [PluginDescriptor] for the plugin.
     */
    private fun getDescriptorInitializer(descriptor: PluginDescriptor) =
        CodeBlock.builder().apply {
            add(
                """
                PluginDescriptor(
                    id = %S,
                    displayName = %S,
                    description = %S,
                    options = listOf(
            
                """.trimIndent(),
                descriptor.id,
                descriptor.displayName,
                descriptor.description
            )

            descriptor.options.forEach {
                add(
                    """
                    |        %T(
                    |            name = %S,
                    |            description = %S,
                    |            type = %T.%L,
                    |            defaultValue = %S,
                    |
                    """.trimMargin(),
                    PluginOption::class,
                    it.name,
                    it.description,
                    PluginOptionType::class,
                    it.type.name,
                    it.defaultValue
                )

                if (it.aliases.isNotEmpty()) {
                    add("            aliases = listOf(\n")

                    it.aliases.forEach { alias ->
                        add("                %S,\n", alias)
                    }

                    add("            ),\n")
                } else {
                    add("            aliases = emptyList(),\n")
                }

                add(
                    """    
                    |            isNullable = %L,
                    |            isRequired = %L
                    |        ),
                    |
                    """.trimMargin(),
                    it.isNullable,
                    it.isRequired
                )
            }

            add(
                """
                    )
                )
                """.trimIndent()
            )
        }.build()
}
