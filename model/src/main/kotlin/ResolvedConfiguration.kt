/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.util.StdConverter

import org.ossreviewtoolkit.utils.common.getDuplicates

/**
 * A container which holds all resolved data which augments ORT's automatically obtained data, like
 * package curations, package configurations, rule violation resolutions and issue resolutions.
 *
 * TODO: Add further data.
 */
data class ResolvedConfiguration(
    @JsonInclude(value = JsonInclude.Include.CUSTOM, valueFilter = PackageCurationsFilter::class)
    val packageCurations: PackageCurations = PackageCurations(),
) {
    /**
     * Return all [PackageCuration]s contained in this [ResolvedConfiguration] in highest-priority-first order.
     */
    @JsonIgnore
    fun getAllPackageCurations(): List<PackageCuration> =
        packageCurations.providers.flatMap { id ->
            packageCurations.data[id].orEmpty()
        }
}

@JsonSerialize(converter = PackageCurationsConverter::class)
data class PackageCurations(
    /**
     * All enabled providers ordered highest-priority-first.
     */
    val providers: List<String> = emptyList(),

    /**
     * All package curations applicable to the packages contained in the enclosing [OrtResult].
     */
    val data: Map<String, List<PackageCuration>> = emptyMap()
) {
    @JsonIgnore
    fun isEmpty(): Boolean =
        providers.isEmpty() && data.isEmpty()

    init {
        val duplicateProviders = providers.getDuplicates()
        require(duplicateProviders.isEmpty()) {
            "The list 'providers' contains the following duplicates, which is not allowed: " +
                    "${duplicateProviders.joinToStringSingleQuoted()}."
        }

        val invalidProviderReferences = data.keys.filter { it !in providers }
        require(invalidProviderReferences.isEmpty()) {
            "The following keys in 'data' reference a non-existing provider, which is not allowed: " +
                    "${invalidProviderReferences.joinToStringSingleQuoted()}."
        }
    }
}

@Suppress("EqualsOrHashCode", "EqualsWithHashCodeExist") // The class is not supposed to be used with hashing.
private class PackageCurationsFilter {
    override fun equals(other: Any?): Boolean =
        other is PackageCurations && other.isEmpty()
}

/**
 * Sorts the keys of [PackageCurations.data] alphabetically and the values of [PackageCurations.data] by
 * their [id][PackageCuration.id]. Drops entries for provider without curations and removes duplicate curations for
 * each provider.
 */
private class PackageCurationsConverter : StdConverter<PackageCurations, PackageCurations>() {
    override fun convert(value: PackageCurations): PackageCurations {
        val sortedData = value.data.filter { (_, curations) ->
            curations.isNotEmpty()
        }.mapValues { (_, curations) ->
            curations.distinct().sortedBy { curation -> curation.id }
        }.toList().sortedBy { (provider, _) -> provider }.toMap()

        return value.copy(data = sortedData)
    }
}

private fun Collection<Any>.joinToStringSingleQuoted() = joinToString(prefix = "'", separator = "','", postfix = "'")
