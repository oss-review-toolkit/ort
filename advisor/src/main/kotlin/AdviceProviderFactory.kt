/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.advisor

import org.ossreviewtoolkit.model.config.AdvisorConfiguration
import org.ossreviewtoolkit.utils.common.ConfigurablePluginFactory
import org.ossreviewtoolkit.utils.common.Plugin

/**
 * The extension point for [AdviceProvider]s.
 */
interface AdviceProviderFactory : ConfigurablePluginFactory<AdviceProvider> {
    companion object {
        val ALL = Plugin.getAll<AdviceProviderFactory>()
    }

    override fun create(config: Map<String, String>): AdviceProvider = create(parseConfig(config))

    /**
     * Create a new [AdviceProvider] with [config].
     */
    fun create(config: AdvisorConfiguration): AdviceProvider

    /**
     * Parse the [config] map into an object.
     */
    fun parseConfig(config: Map<String, String>): AdvisorConfiguration
}
