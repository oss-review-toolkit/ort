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
import com.google.devtools.ksp.processing.Dependencies

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addAll
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class JsonSpecGenerator(private val codeGenerator: CodeGenerator) {
    private val json = Json {
        prettyPrint = true
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun generate(pluginSpec: PluginSpec) {
        val jsonObject = buildJsonObject {
            putJsonObject("descriptor") {
                put("id", pluginSpec.descriptor.id)
                put("displayName", pluginSpec.descriptor.displayName)
                put("description", pluginSpec.descriptor.description)

                putJsonArray("options") {
                    pluginSpec.descriptor.options.forEach {
                        addJsonObject {
                            put("name", it.name)
                            put("type", it.type.name)
                            put("description", it.description)
                            put("default", it.defaultValue)

                            putJsonArray("aliases") {
                                addAll(it.aliases)
                            }

                            put("isRequired", it.isRequired)
                        }
                    }
                }
            }

            put("configClass", pluginSpec.configClass?.typeName?.toString())
            put("factoryClass", pluginSpec.factory.qualifiedName)
        }

        codeGenerator.createNewFileByPath(
            dependencies = Dependencies(aggregating = true, *listOfNotNull(pluginSpec.containingFile).toTypedArray()),
            path = "META-INF/plugin/${pluginSpec.packageName}.${pluginSpec.descriptor.id}",
            extensionName = "json"
        ).use { output ->
            json.encodeToStream(jsonObject, output)
        }
    }
}
