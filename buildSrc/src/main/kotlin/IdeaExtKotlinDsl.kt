/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2010-2019 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package com.here.ort.gradle

import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionAware
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.plugins.ide.idea.model.IdeaProject

import org.jetbrains.gradle.ext.DefaultRunConfigurationContainer
import org.jetbrains.gradle.ext.ProjectSettings
import org.jetbrains.gradle.ext.RunConfiguration
import org.jetbrains.gradle.ext.TaskTriggersConfig

// The following extension functions add the missing Kotlin DSL syntactic sugar for nicely configuring the idea-ext
// plugin.

// The code is derived from the one at [1] which is Copyright 2010-2019 JetBrains s.r.o. and governed under the
// Apache 2.0 license.
//
// [1] https://github.com/JetBrains/kotlin/blob/27fd64ed4276f907079f41920fda3c4a6d530b2a/buildSrc/src/main/kotlin/ideaExtKotlinDsl.kt

fun Project.idea(block: IdeaModel.() -> Unit) =
    (this as ExtensionAware).extensions.configure("idea", block)

fun IdeaProject.settings(block: ProjectSettings.() -> Unit) =
    (this@settings as ExtensionAware).extensions.configure("settings", block)

fun ProjectSettings.taskTriggers(block: TaskTriggersConfig.() -> Unit) =
    (this@taskTriggers as ExtensionAware).extensions.configure("taskTriggers", block)

fun ProjectSettings.runConfigurations(block: DefaultRunConfigurationContainer.() -> Unit) =
    (this@runConfigurations as ExtensionAware).extensions.configure("runConfigurations", block)

inline fun <reified T : RunConfiguration> DefaultRunConfigurationContainer.defaults(noinline block: T.() -> Unit) =
    defaults(T::class.java, block)
