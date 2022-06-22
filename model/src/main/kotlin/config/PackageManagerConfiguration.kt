/*
 * Copyright (C) 2022 Bosch.IO GmbH
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

package org.ossreviewtoolkit.model.config

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * The configuration for a package manager.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class PackageManagerConfiguration(
    /**
     * A list of package manager names that this package manager must run after. For example, this can be used, if
     * another package manager generates files that this package manager requires to run correctly.
     */
    val mustRunAfter: List<String>? = null,

    /**
     * Custom configuration options for the package manager. See the documentation of the respective class for available
     * options.
     */
    val options: Options? = null
) {
    /**
     * Merge this [PackageManagerConfiguration] with [other]. Values of [other] take precedence.
     */
    fun merge(other: PackageManagerConfiguration): PackageManagerConfiguration {
        val mergedOptions = when {
            options == null -> other.options
            other.options == null -> options
            else -> options.toMutableMap() + other.options
        }

        return PackageManagerConfiguration(
            mustRunAfter = other.mustRunAfter ?: mustRunAfter,
            options = mergedOptions
        )
    }
}
