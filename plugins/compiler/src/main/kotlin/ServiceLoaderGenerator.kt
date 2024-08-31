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

/**
 * A class to generate service loader files for plugin factories.
 */
class ServiceLoaderGenerator(private val codeGenerator: CodeGenerator) {
    fun generate(serviceLoaderSpecs: List<ServiceLoaderSpec>) {
        serviceLoaderSpecs.groupBy { it.pluginSpec.factory.qualifiedName }.forEach { (factoryName, specs) ->
            val containingFiles = specs.mapNotNull { it.pluginSpec.containingFile }

            codeGenerator.createNewFileByPath(
                dependencies = Dependencies(
                    aggregating = true,
                    *containingFiles.toTypedArray()
                ),
                path = "META-INF/services/$factoryName",
                extensionName = ""
            ).use { output ->
                output.writer().use { writer ->
                    specs.forEach { spec ->
                        writer.write("${spec.pluginSpec.packageName}.${spec.factory.name}\n")
                    }
                }
            }
        }
    }
}
