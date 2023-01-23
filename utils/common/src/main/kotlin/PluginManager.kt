/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.utils.common

import java.util.ServiceLoader

/**
 * Return a [ServiceLoader] that is capable of loading services of type [T].
 */
inline fun <reified T : Any> getLoaderFor(): ServiceLoader<T> = ServiceLoader.load(T::class.java)

/**
 * An interface to be implemented by any ORT plugin.
 */
interface Plugin {
    companion object {
        /**
         * Return instances for all ORT plugins of type [T].
         */
        inline fun <reified T : Plugin> getAll() = getLoaderFor<T>()
            .iterator()
            .asSequence()
            .associateByTo(sortedMapOf(String.CASE_INSENSITIVE_ORDER)) {
                it.type
            }
    }

    /**
     * The type of the plugin.
     */
    val type: String
}

/**
 * An interface to be implemented by plugin factories. Plugin factories are required if a plugin needs configuration
 * on initialization and can therefore not be created directly by the [ServiceLoader].
 */
interface ConfigurablePluginFactory<out PLUGIN> : Plugin {
    /**
     * Create a new instance of [PLUGIN] from [config].
     */
    fun create(config: Map<String, String>): PLUGIN
}
