/*
 * Copyright (C) 2020-2022 Bosch.IO GmbH
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
import org.ossreviewtoolkit.utils.ort.ORT_CONFIG_FILENAME
import org.ossreviewtoolkit.utils.ort.ortConfigDirectory

/**
 * A common interface for use with [ServiceLoader] that all [AbstractAdviceProviderFactory] classes need to
 * implement.
 */
interface AdviceProviderFactory {
    /**
     * The name to use to refer to the provider.
     */
    val providerName: String

    /**
     * Create an [AdviceProvider] using the specified [config].
     */
    fun create(config: AdvisorConfiguration): AdviceProvider
}

/**
 * A generic factory class for an [AdviceProvider].
 */
abstract class AbstractAdviceProviderFactory<out T : AdviceProvider>(
    override val providerName: String
) : AdviceProviderFactory {
    abstract override fun create(config: AdvisorConfiguration): T

    protected fun <T : Any> AdvisorConfiguration.forProvider(select: AdvisorConfiguration.() -> T?): T =
        requireNotNull(select()) {
            "No configuration for '$providerName' found in '${ortConfigDirectory.resolve(ORT_CONFIG_FILENAME)}'."
        }

    /**
     * Return a map with options for the [AdviceProvider] managed by this factory or an empty map if no options are
     * available.
     */
    protected fun AdvisorConfiguration.providerOptions(): Options =
        options.orEmpty()[providerName].orEmpty()

    /**
     * Return the provider's name here to allow Clikt to display something meaningful when listing the advisors which
     * are enabled by default via their factories.
     */
    override fun toString() = providerName
}
