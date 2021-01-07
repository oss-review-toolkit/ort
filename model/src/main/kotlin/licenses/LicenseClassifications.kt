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

import java.util.SortedSet

import org.ossreviewtoolkit.spdx.SpdxExpression
import org.ossreviewtoolkit.spdx.SpdxSingleLicenseExpression

/**
 * Classifications for licenses which allow to assign meta data to licenses. This allows defining rather generic
 * categories and assigning licenses to these. That way flexible classifications can be created based on
 * customizable categories. The available license categories need to be declared explicitly; when creating an
 * instance, it is checked that all the references from the [categorizations] point to existing [categories].
 */
data class LicenseClassifications(
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
    val categorizations: List<LicenseCategorization> = emptyList()
) {
    /** A property for fast look-ups of licenses for a given category. */
    private val licensesByCategoryName: Map<String, Set<LicenseCategorization>> by lazy {
        val result = mutableMapOf<String, MutableSet<LicenseCategorization>>()

        categorizations.forEach { license ->
            license.categories.forEach { categoryId ->
                result.getOrPut(categoryId) { mutableSetOf() } += license
            }
        }

        result
    }

    /** A property for fast-lookups of licenses by their ID. */
    private val licensesById: Map<SpdxSingleLicenseExpression, LicenseCategorization> by lazy {
        categorizations.associateBy { it.id }
    }

    /** A property allowing convenient access to the names of all categories defined. */
    val categoryNames: SortedSet<String> by lazy {
        categories.mapTo(sortedSetOf()) { it.name }
    }

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

        categorizations.associateWith { it.categories.filterNot(categoryNames::contains) }
            .filterNot { it.value.isEmpty() }
            .let { invalidCategorizations ->
                require(invalidCategorizations.isEmpty()) {
                    val licenseIds = invalidCategorizations.keys.joinToString { it.id.toString() }
                    val categories = invalidCategorizations.values.flatten().toSet()
                    "Found licenses that reference non-existing categories: $licenseIds; " +
                            "unknown categories are $categories."
                }
            }
    }

    /**
     * Return a set with the licenses that are assigned to the category with the given [name][categoryName].
     * If the there is no category with the name provided, throw an [IllegalStateException].
     * This is intended to be mostly used via scripting.
     */
    fun getLicensesForCategory(categoryName: String): Set<LicenseCategorization> =
        licensesByCategoryName[categoryName] ?: error("Unknown license category name: $categoryName.")

    /**
     * A convenience operator to return the [LicenseCategorization] for the given [id] or *null* if no such
     * categorization can be found.
     */
    operator fun get(id: SpdxExpression): LicenseCategorization? = licensesById[id]
}

fun LicenseClassifications?.orEmpty(): LicenseClassifications = this ?: LicenseClassifications()
