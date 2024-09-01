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

import org.ossreviewtoolkit.utils.common.getLoaderFor

/**
 * A factory interface for creating plugins of type [PLUGIN]. The different plugin endpoints ORT provides must inherit
 * from this interface.
 */
interface PluginFactory<out PLUGIN : Plugin> {
    companion object {
        /**
         * Return all plugin factories of type [FACTORY].
         */
        inline fun <reified FACTORY : PluginFactory<PLUGIN>, PLUGIN> getAll() =
            getLoaderFor<FACTORY>()
                .iterator()
                .asSequence()
                .associateByTo(sortedMapOf(String.CASE_INSENSITIVE_ORDER)) {
                    it.descriptor.id
                }
    }

    /**
     * The descriptor of the plugin
     */
    val descriptor: PluginDescriptor

    /**
     * Create a new instance of [PLUGIN] from [config].
     */
    fun create(config: PluginConfig): PLUGIN
}

/**
 * A plugin that ORT can use. Each plugin extension point of ORT must inherit from this interface.
 */
interface Plugin {
    val descriptor: PluginDescriptor
}
