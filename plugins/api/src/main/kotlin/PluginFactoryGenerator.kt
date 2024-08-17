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

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.Modifier

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo

class PluginFactoryGenerator(private val codeGenerator: CodeGenerator) {
    fun generate(ortPlugin: OrtPlugin, pluginClass: KSClassDeclaration, pluginFactoryClass: KSClassDeclaration) {
        val generatedFactory = generateFactoryClass(ortPlugin, pluginClass, pluginFactoryClass)
        generateServiceLoaderFile(pluginClass, pluginFactoryClass, generatedFactory)
    }

    /**
     * Generate a factory class for the [ortPlugin] of type [pluginClass] that implements the [pluginFactoryClass]
     * interface.
     */
    private fun generateFactoryClass(
        ortPlugin: OrtPlugin,
        pluginClass: KSClassDeclaration,
        pluginFactoryClass: KSClassDeclaration
    ): TypeSpec {
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

        // Create the initializer for the plugin config object.
        val configInitializer = configType?.let { getConfigInitializer(it, pluginOptions) }

        // Create the plugin descriptor property.
        val descriptorInitializer = getDescriptorInitializer(ortPlugin, pluginClass, pluginOptions)
        val descriptorProperty = PropertySpec.builder("descriptor", PluginDescriptor::class, KModifier.OVERRIDE)
            .initializer(descriptorInitializer)
            .build()

        // Create the create function that initializes the plugin with the descriptor and the config object.
        val createFunction = FunSpec.builder("create").apply {
            addModifiers(KModifier.OVERRIDE)
            addParameter("config", PluginConfig::class)

            if (configInitializer != null) {
                addCode(configInitializer)
                addCode("return %T(%N, configObject)", pluginType, descriptorProperty)
            } else {
                addCode("return %T(%N)", pluginType, descriptorProperty)
            }

            returns(pluginType)
        }.build()

        // Create the factory class.
        val className = "${pluginClass.simpleName.asString()}Factory"
        val classSpec = TypeSpec.classBuilder(className)
            .addSuperinterface(pluginFactoryType)
            .addProperty(descriptorProperty)
            .addFunction(createFunction)
            .build()

        // Write the factory class to a file.
        FileSpec.builder(ClassName(pluginClass.packageName.asString(), className))
            .addType(classSpec)
            .build()
            .writeTo(codeGenerator, aggregating = true, originatingKSFiles = listOfNotNull(pluginClass.containingFile))

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
                if (option.defaultValue != null) {
                    when (option.type) {
                        PluginOptionType.BOOLEAN -> add(" ?: %L", option.defaultValue.toBoolean())
                        PluginOptionType.INTEGER -> add(" ?: %L", option.defaultValue.toInt())
                        PluginOptionType.LONG -> add(" ?: %LL", option.defaultValue.toLong())
                        PluginOptionType.SECRET -> add(" ?: %T(%S)", Secret::class, option.defaultValue)
                        PluginOptionType.STRING -> add(" ?: %S", option.defaultValue)
                        PluginOptionType.STRING_LIST -> add(" ?: %S", option.defaultValue)
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
    private fun getDescriptorInitializer(
        ortPlugin: OrtPlugin,
        pluginClass: KSClassDeclaration,
        pluginOptions: List<PluginOption>
    ) = CodeBlock.builder().apply {
        add(
            """
            PluginDescriptor(
                name = %S,
                className = %S,
                description = %S,
                options = listOf(
            
            """.trimIndent(),
            ortPlugin.name,
            pluginClass.simpleName.asString(),
            ortPlugin.description
        )

        pluginOptions.forEach {
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

        require(constructors.size == 1) {
            "Plugin class $pluginClass must have exactly one constructor with a PluginDescriptor and a config " +
                "argument."
        }

        return constructors.first()
    }

    /**
     * Get the plugin options from the config class by mapping its properties to [PluginOption] instances.
     */
    @OptIn(KspExperimental::class)
    private fun KSClassDeclaration.getPluginOptions(): List<PluginOption> {
        require(Modifier.DATA in modifiers) {
            "Config class $this must be a data class."
        }

        require(getConstructors().toList().size == 1) {
            "Config class $this must have exactly one constructor."
        }

        val constructor = getConstructors().single()

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

            val defaultValue = annotation?.defaultValue

            PluginOption(
                name = param.name?.asString().orEmpty(),
                description = prop.docString?.trim().orEmpty(),
                type = type,
                defaultValue = defaultValue,
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

    /**
     * Generate a service loader file for the plugin factory.
     */
    private fun generateServiceLoaderFile(
        pluginClass: KSClassDeclaration,
        pluginFactoryClass: KSClassDeclaration,
        generatedFactory: TypeSpec
    ) {
        codeGenerator.createNewFileByPath(
            dependencies = Dependencies(aggregating = false, *listOfNotNull(pluginClass.containingFile).toTypedArray()),
            path = "META-INF/services/${pluginFactoryClass.qualifiedName?.asString()}",
            extensionName = ""
        ).use { output ->
            output.writer().use { writer ->
                writer.write("${pluginClass.packageName.asString()}.${generatedFactory.name}\n")
            }
        }
    }
}
