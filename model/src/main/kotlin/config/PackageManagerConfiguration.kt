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

package org.ossreviewtoolkit.model.config

import com.fasterxml.jackson.annotation.JsonInclude

import org.ossreviewtoolkit.utils.common.Options

/**
 * The configuration model for a package manager. This class is (de-)serialized in the following places:
 * - Deserialized from "config.yml" as part of [OrtConfiguration] (via Hoplite).
 * - Deserialized from ".ort.yml" as part of [RepositoryAnalyzerConfiguration] (via Jackson)
 * - (De-)Serialized as part of [org.ossreviewtoolkit.model.OrtResult] (via Jackson).
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
     * Merge this [PackageManagerConfiguration] with [override]. Values of [override] take precedence.
     */
    fun merge(override: PackageManagerConfiguration): PackageManagerConfiguration {
        val mergedOptions = when {
            options == null -> override.options
            override.options == null -> options
            else -> options.toMutableMap() + override.options
        }

        return PackageManagerConfiguration(
            mustRunAfter = override.mustRunAfter ?: mustRunAfter,
            options = mergedOptions
        )
    }
}
