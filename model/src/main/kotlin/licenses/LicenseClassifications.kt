/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.model.licenses

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude

import java.util.SortedSet

import org.ossreviewtoolkit.utils.common.getDuplicates
import org.ossreviewtoolkit.utils.spdx.SpdxExpression
import org.ossreviewtoolkit.utils.spdx.SpdxSingleLicenseExpression

/**
 * Classifications for licenses which allow assigning metadata to licenses. This allows defining rather generic
 * categories and assigning licenses to these. That way flexible classifications can be created based on
 * customizable categories. The available license categories need to be declared explicitly; when creating an
 * instance, it is checked that all the references from the [categorizations] point to existing [categories].
 */
data class LicenseClassifications(
    /**
     * Defines metadata for the license categories.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val categories: List<LicenseCategory> = emptyList(),

    /**
     * Defines metadata for licenses.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val categorizations: List<LicenseCategorization> = emptyList()
) {
    /** A property for fast look-ups of licenses for a given category. */
    @get:JsonIgnore
    val licensesByCategory: Map<String, Set<SpdxSingleLicenseExpression>> by lazy {
        buildMap<String, MutableSet<SpdxSingleLicenseExpression>> {
            categorizations.forEach { license ->
                license.categories.forEach { category ->
                    getOrPut(category) { mutableSetOf() } += license.id
                }
            }

            categories.forEach { category ->
                putIfAbsent(category.name, mutableSetOf())
            }
        }
    }

    /** A property for fast look-ups of categories for a given license. */
    @get:JsonIgnore
    val categoriesByLicense: Map<SpdxSingleLicenseExpression, Set<String>> by lazy {
        categorizations.associate { it.id to it.categories }
    }

    /** A property allowing convenient access to the names of all categories defined. */
    @get:JsonIgnore
    val categoryNames: SortedSet<String> by lazy {
        categories.mapTo(sortedSetOf()) { it.name }
    }

    init {
        categories.getDuplicates { it.name }.let { duplicates ->
            require(duplicates.isEmpty()) {
                "Found multiple license categories with the same name: ${duplicates.keys}"
            }
        }

        categorizations.getDuplicates { it.id }.let { duplicates ->
            require(duplicates.isEmpty()) {
                "Found multiple license categorizations with the same id: ${duplicates.keys}"
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
     * A convenience operator to return the categories for the given license [id], or null if the license is not
     * categorized.
     */
    operator fun get(id: SpdxExpression) = categoriesByLicense[id]

    /** A convenience function to check whether there is a categorization for the given license [id]. */
    fun isCategorized(id: SpdxExpression) = id in categoriesByLicense

    /**
     * Merge [other] into these classifications, overwriting any conflicting existing classifications.
     */
    fun merge(other: LicenseClassifications): LicenseClassifications {
        val filteredCategoriesByLicense = mutableMapOf<SpdxSingleLicenseExpression, MutableSet<String>>()

        // Remove categories that are also used in the other classification as different classifications might use
        // different semantics for the same category name.
        categorizations.forEach { (id, categories) ->
            val filteredCategories = categories.filterTo(mutableSetOf()) { it !in other.categoryNames }
            if (filteredCategories.isNotEmpty()) filteredCategoriesByLicense[id] = filteredCategories
        }

        // Merge other into existing categories for each license.
        other.categorizations.forEach { (id, categories) ->
            filteredCategoriesByLicense.getOrPut(id) { mutableSetOf() } += categories
        }

        val usedCategories = filteredCategoriesByLicense.values.flatten().toSet()

        return LicenseClassifications(
            categories = (categories + other.categories).toSet().filter { it.name in usedCategories },
            categorizations = filteredCategoriesByLicense.map { (id, categories) ->
                LicenseCategorization(id, categories)
            }
        )
    }
}

/**
 * A convenience extension function to return empty LicenseClassifications for null.
 */
fun LicenseClassifications?.orEmpty(): LicenseClassifications = this ?: LicenseClassifications()
