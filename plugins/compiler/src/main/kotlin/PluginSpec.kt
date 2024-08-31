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

import com.google.devtools.ksp.symbol.KSFile

import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec

import org.ossreviewtoolkit.plugins.api.PluginDescriptor

/**
 * A specification for a plugin.
 */
data class PluginSpec(
    val containingFile: KSFile?,
    val descriptor: PluginDescriptor,
    val packageName: String,
    val typeName: TypeName,
    val configClass: PluginConfigClassSpec?,
    val factory: PluginFactorySpec
)

/**
 * A specification for a plugin configuration class.
 */
data class PluginConfigClassSpec(
    val typeName: TypeName
)

/**
 * A specification for a plugin factory. This describes the base factory class that the generated factory should
 * implement.
 */
data class PluginFactorySpec(
    val typeName: TypeName,
    val qualifiedName: String
)

/**
 * A specification for a service loader, used to generate service loader files.
 */
data class ServiceLoaderSpec(
    val pluginSpec: PluginSpec,
    val factory: TypeSpec
)
