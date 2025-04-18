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

import kotlin.reflect.KClass

/**
 * An annotation to mark a class as an ORT plugin. It is used to generate a factory class to create instances of the
 * plugin.
 *
 * Each ORT extension point is represented by a class that extends [PluginFactory] and each plugin must provide a
 * factory class that implements the extension point. The plugin factory class is responsible for creating instances of
 * the plugins, handling the [PluginConfig], and providing the [PluginDescriptor].
 */
@Target(AnnotationTarget.CLASS)
annotation class OrtPlugin(
    /**
     * The id of the plugin. Must be unique among all plugins for the same extension point. If empty, the id is derived
     * from the class name by removing the plugin's parent class name (with any "Ort" prefix stripped) as a suffix.
     */
    val id: String = "",

    /** The display name of the plugin. */
    val displayName: String,

    /** The description of the plugin. */
    val description: String,

    /** The factory class that represents the ORT extension point for this plugin. */
    val factory: KClass<*>
)
