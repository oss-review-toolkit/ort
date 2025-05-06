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
import com.google.devtools.ksp.getAllSuperTypes
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration

import org.ossreviewtoolkit.plugins.api.OrtPlugin

class PluginProcessor(codeGenerator: CodeGenerator) : SymbolProcessor {
    /**
     * True, if the processor has been invoked in a previous run.
     */
    private var invoked = false

    private val specFactory = PluginSpecFactory()
    private val factoryGenerator = PluginFactoryGenerator(codeGenerator)
    private val jsonGenerator = JsonSpecGenerator(codeGenerator)
    private val serviceLoaderGenerator = ServiceLoaderGenerator(codeGenerator)

    /**
     * Process all classes annotated with [OrtPlugin] to generate plugin factories for them.
     */
    @OptIn(KspExperimental::class)
    override fun process(resolver: Resolver): List<KSAnnotated> {
        if (invoked) return emptyList()

        val ortPluginClassName = OrtPlugin::class.qualifiedName

        requireNotNull(ortPluginClassName) {
            "Could not get qualified name of OrtPlugin annotation."
        }

        val serviceLoaderSpecs = mutableListOf<ServiceLoaderSpec>()

        resolver.getSymbolsWithAnnotation(ortPluginClassName).forEach { pluginClass ->
            require(pluginClass is KSClassDeclaration) {
                "Annotated element $pluginClass is not a class."
            }

            val pluginAnnotation = pluginClass.getAnnotationsByType(OrtPlugin::class).single()

            val pluginFactoryClass = resolver.getPluginFactoryClass(pluginAnnotation)
            checkExtendsPluginFactory(pluginFactoryClass)

            val pluginBaseClass = getPluginBaseClass(pluginFactoryClass)
            checkExtendsPluginClass(pluginClass, pluginBaseClass)

            val pluginSpec = specFactory.create(pluginAnnotation, pluginClass, pluginBaseClass, pluginFactoryClass)
            serviceLoaderSpecs += factoryGenerator.generate(pluginSpec)
            jsonGenerator.generate(pluginSpec)
        }

        serviceLoaderGenerator.generate(serviceLoaderSpecs)

        invoked = true

        return emptyList()
    }

    /**
     * Get the declaration of the [plugin factory][OrtPlugin.factory].
     */
    private fun Resolver.getPluginFactoryClass(annotation: OrtPlugin): KSClassDeclaration {
        val pluginFactoryName = annotation.factory.qualifiedName

        requireNotNull(pluginFactoryName) {
            "Could not get qualified name of plugin factory."
        }

        val pluginFactoryClass = getClassDeclarationByName(pluginFactoryName)

        requireNotNull(pluginFactoryClass) {
            "Could not find class for plugin factory $pluginFactoryName."
        }

        return pluginFactoryClass
    }

    /**
     * Get the declaration of the plugin class created by [factoryClass].
     */
    private fun getPluginBaseClass(factoryClass: KSClassDeclaration): KSClassDeclaration {
        val baseClass = factoryClass
            .getAllFunctions()
            .single { it.simpleName.asString() == "create" }
            .returnType
            ?.resolve()
            ?.declaration

        checkNotNull(baseClass)

        require(baseClass is KSClassDeclaration) {
            "Plugin class $baseClass is not a class."
        }

        return baseClass
    }

    /**
     * Ensure that [factoryClass] extends [org.ossreviewtoolkit.plugins.api.PluginFactory].
     */
    private fun checkExtendsPluginFactory(factoryClass: KSClassDeclaration) =
        require(
            factoryClass.getAllSuperTypes().any {
                it.declaration.qualifiedName?.asString() == "org.ossreviewtoolkit.plugins.api.PluginFactory"
            }
        ) {
            "Plugin factory $factoryClass does not extend the required super type PluginFactory."
        }

    /**
     * Ensure that [pluginClass] extends [pluginBaseClass].
     */
    private fun checkExtendsPluginClass(pluginClass: KSClassDeclaration, pluginBaseClass: KSClassDeclaration) {
        require(pluginClass.getAllSuperTypes().any { it.declaration == pluginBaseClass }) {
            "Plugin class $pluginClass does not extend the required super type $pluginBaseClass."
        }
    }
}
