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

import com.google.devtools.ksp.processing.CodeGenerator

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
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
        // Create the initializer for the plugin config object.
        val configInitializer = pluginSpec.configClass?.let {
            getConfigInitializer(it.typeName, pluginSpec.descriptor.options)
        }

        // Create the plugin descriptor property.
        val descriptorInitializer = getDescriptorInitializer(pluginSpec.descriptor)
        val descriptorProperty = PropertySpec.builder("descriptor", PluginDescriptor::class)
            .initializer(descriptorInitializer)
            .build()

        val companionObject = TypeSpec.companionObjectBuilder()
            .addProperty(descriptorProperty)
            .build()

        val descriptorDelegateProperty = PropertySpec.builder("descriptor", PluginDescriptor::class, KModifier.OVERRIDE)
            .delegate("Companion::descriptor")
            .build()

        // Create the create function that initializes the plugin with the descriptor and the config object.
        val createFunction = FunSpec.builder("create").apply {
            addModifiers(KModifier.OVERRIDE)
            addParameter("config", PluginConfig::class)

            if (configInitializer != null) {
                addCode(configInitializer)
                addCode("return %T(%N, configObject)", pluginSpec.typeName, descriptorProperty)
            } else {
                addCode("return %T(%N)", pluginSpec.typeName, descriptorProperty)
            }

            returns(pluginSpec.typeName)
        }.build()

        // Create the factory class.
        val className = "${pluginSpec.typeName.toString().substringAfterLast('.')}Factory"
        val classSpec = TypeSpec.classBuilder(className)
            .addSuperinterface(pluginSpec.factory.typeName)
            .addType(companionObject)
            .addProperty(descriptorDelegateProperty)
            .addFunction(createFunction)
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
    private fun getConfigInitializer(configType: TypeName, pluginOptions: List<PluginOption>) =
        CodeBlock.builder().apply {
            add("val configObject = %T(\n", configType)

            pluginOptions.forEach { option ->
                add("    ${option.name} = ")

                // Add code to read the option from the options or secrets maps based on its type.
                when (option.type) {
                    PluginOptionType.BOOLEAN -> add("config.options[%S]?.toBooleanStrict()", option.name)
                    PluginOptionType.INTEGER -> add("config.options[%S]?.toInt()", option.name)
                    PluginOptionType.LONG -> add("config.options[%S]?.toLong()", option.name)
                    PluginOptionType.SECRET -> add("config.secrets[%S]?.let { %T(it) }", option.name, Secret::class)
                    PluginOptionType.STRING -> add("config.options[%S]", option.name)
                    PluginOptionType.STRING_LIST -> add(
                        "config.options[%S]?.split(\",\")?.map { it.trim() }",
                        option.name
                    )
                }

                // Add the default value if present.
                option.defaultValue?.let { defaultValue ->
                    when (option.type) {
                        PluginOptionType.BOOLEAN -> add(" ?: %L", defaultValue.toBoolean())
                        PluginOptionType.INTEGER -> add(" ?: %L", defaultValue.toInt())
                        PluginOptionType.LONG -> add(" ?: %LL", defaultValue.toLong())
                        PluginOptionType.SECRET -> add(" ?: %T(%S)", Secret::class, defaultValue)
                        PluginOptionType.STRING -> add(" ?: %S", defaultValue)
                        PluginOptionType.STRING_LIST -> add(" ?: %S", defaultValue)
                    }
                }

                // Throw exception if the option is required but not set.
                if (option.isRequired) {
                    add(" ?: error(%S)", "Option ${option.name} is required but not set.")
                }

                add(",\n")
            }

            // TODO: Decide if an exception should be thrown if the options or secrets maps contain values that do not
            //       match any plugin option. This would be a safety net to catch typos in option names.

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
                    |            isRequired = %L
                    |        ),
                    |
                    """.trimMargin(),
                    PluginOption::class,
                    it.name,
                    it.description,
                    PluginOptionType::class,
                    it.type.name,
                    it.defaultValue,
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
