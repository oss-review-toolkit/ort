/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.model.licenses

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonInclude

/**
 * A classification for licenses which allows to assign meta data to licenses. It allows defining rather generic
 * categories and assigning licenses to these. That way flexible classifications can be created based on
 * customizable categories. The available license categories need to be declared explicitly; when creating an
 * instance, it is checked that all the references from the [categorizations] point to existing [categories].
 */
data class LicenseConfiguration(
    /**
     * Defines meta data for the license categories.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonAlias("license_sets")
    val categories: List<LicenseCategory> = emptyList(),

    /**
     * Defines meta data for licenses.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonAlias("licenses")
    val categorizations: List<License> = emptyList()
) {
    init {
        categories.groupBy { it.name }.values.filter { it.size > 1 }.let { groups ->
            require(groups.isEmpty()) {
                "Found multiple license category entries with the same name: " +
                        groups.joinToString { it.first().name }
            }
        }

        categorizations.groupBy { it.id }.values.filter { it.size > 1 }.let { groups ->
            require(groups.isEmpty()) {
                "Found multiple license entries with the same Id: ${groups.joinToString { it.first().id.toString() }}."
            }
        }

        val categoryNames = categories.map { it.name }.toSet()
        categorizations.filterNot { it.categories.all(categoryNames::contains) }.let { lic ->
            require(lic.isEmpty()) {
                "Found licenses that reference non-existing categories: ${lic.joinToString { it.id.toString() }}"
            }
        }
    }

    /**
     * A property for fast look-ups of licenses for a given category.
     */
    private val licensesByCategoryName: Map<String, Set<License>> by lazy {
        val result = mutableMapOf<String, MutableSet<License>>()

        categories.forEach { category ->
            result[category.name] = mutableSetOf()
        }

        categorizations.forEach { license ->
            license.categories.forEach { categoryId ->
                result.getOrPut(categoryId) { mutableSetOf() } += license
            }
        }

        result
    }

    @Suppress("UNUSED") // This is intended to be mostly used via scripting.
    fun getLicensesForCategory(categoryName: String): Set<License> =
        licensesByCategoryName[categoryName] ?: error("Unknown license category name: $categoryName.")
}

fun LicenseConfiguration?.orEmpty(): LicenseConfiguration = this ?: LicenseConfiguration()
