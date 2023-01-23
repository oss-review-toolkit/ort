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

import java.util.ServiceLoader

import org.ossreviewtoolkit.model.config.AdvisorConfiguration
import org.ossreviewtoolkit.model.config.Options
import org.ossreviewtoolkit.utils.common.Plugin
import org.ossreviewtoolkit.utils.ort.ORT_CONFIG_FILENAME
import org.ossreviewtoolkit.utils.ort.ortConfigDirectory

/**
 * A common interface for use with [ServiceLoader] that all [AbstractAdviceProviderFactory] classes need to
 * implement.
 */
interface AdviceProviderFactory : Plugin {
    /**
     * Create an [AdviceProvider] using the specified [config].
     */
    fun create(config: AdvisorConfiguration): AdviceProvider
}

/**
 * A generic factory class for an [AdviceProvider].
 */
abstract class AbstractAdviceProviderFactory<out T : AdviceProvider>(
    override val type: String
) : AdviceProviderFactory {
    abstract override fun create(config: AdvisorConfiguration): T

    /**
     * For providers that require configuration, return the typed configuration dedicated to provider [T] or throw if it
     * does not exist.
     */
    protected fun <T : Any> AdvisorConfiguration.forProvider(select: AdvisorConfiguration.() -> T?): T =
        requireNotNull(select()) {
            "No configuration for '$type' found in '${ortConfigDirectory.resolve(ORT_CONFIG_FILENAME)}'."
        }

    /**
     * Return a map with options for the [AdviceProvider] managed by this factory or an empty map if no options are
     * available.
     */
    protected fun AdvisorConfiguration.providerOptions(): Options =
        options.orEmpty()[type].orEmpty()

    /**
     * Return the provider's name here to allow Clikt to display something meaningful when listing the advisors which
     * are enabled by default via their factories.
     */
    override fun toString() = type
}
