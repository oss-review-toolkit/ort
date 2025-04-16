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

package org.ossreviewtoolkit.helper.commands

import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.helper.utils.OrtHelperCommand
import org.ossreviewtoolkit.model.licenses.LicenseCategorization
import org.ossreviewtoolkit.model.licenses.LicenseClassifications
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.utils.common.expandTilde

internal class ListLicenseCategoriesCommand : OrtHelperCommand(
    help = "Lists the license categories."
) {
    private val licenseClassificationsFile by option(
        "--license-classifications-file", "-i",
        help = "The license classifications file."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val groupByCategory by option(
        "--group-by-category",
        help = "If set, the licenses are listed separately per category."
    ).flag()

    override fun run() {
        val licenseClassifications = licenseClassificationsFile.readValue<LicenseClassifications>()

        println(licenseClassifications.summary())

        if (groupByCategory) {
            println(licenseClassifications.licensesByCategory())
        } else {
            println(licenseClassifications.licensesList())
        }
    }

    private fun LicenseClassifications.summary(): String =
        buildString {
            appendLine("Found ${categorizations.size} licenses categorized as:\n")

            categorizations.groupByCategory().toList().sortedBy { it.first }.forEach { (category, licenses) ->
                appendLine("  $category (${licenses.size})")
            }
        }

    private fun LicenseClassifications.licensesByCategory(): String =
        buildString {
            categorizations.groupByCategory().forEach { (category, licenses) ->
                appendLine("$category (${licenses.size}):")
                appendLine()

                licenses.sortedBy { it.id.toString() }.forEach { license ->
                    appendLine("  ${license.description(category)}")
                }

                appendLine()
            }
        }

    private fun LicenseClassifications.licensesList(): String =
        buildString {
            categorizations.sortedBy { it.id.toString() }.forEach { license ->
                appendLine(license.description())
            }
        }

    private fun LicenseCategorization.description(ignoreCategory: String? = null): String {
        val filteredCategories = categories.filterNot { it == ignoreCategory }

        return buildString {
            append(id)
            if (filteredCategories.isNotEmpty()) append(": [${filteredCategories.joinToString()}]")
        }
    }

    private fun Collection<LicenseCategorization>.groupByCategory(): Map<String, List<LicenseCategorization>> =
        flatMap { license ->
            license.categories.map { category -> license to category }
        }.groupBy({ it.second }, { it.first })
}
