/*
 * Copyright (C) 2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.helper.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.model.licenses.License
import org.ossreviewtoolkit.model.licenses.LicenseConfiguration
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.utils.expandTilde

class ListLicenseCategoriesCommand : CliktCommand(
    help = "Lists the license categories."
) {
    private val licenseConfigurationFile by option(
        "--license-configuration-file",
        help = "The license configuration file."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = true)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val groupByCategory by option(
        "--group-by-category",
        help = "If set, the licenses are listed separately per category."
    ).flag()

    override fun run() {
        val licenseConfiguration = licenseConfigurationFile.readValue<LicenseConfiguration>()

        println(licenseConfiguration.summary())

        if (groupByCategory) {
            println(licenseConfiguration.licensesByCategory())
        } else {
            println(licenseConfiguration.licensesList())
        }
    }

    private fun LicenseConfiguration.summary(): String =
        buildString {
            appendLine("Found ${licenses.size} licenses categorized as:\n")

            licenses.groupByCategory().toList().sortedBy { it.first }.forEach { (category, licenses) ->
                appendLine("  $category (${licenses.size})")
            }
        }

    private fun LicenseConfiguration.licensesByCategory(): String =
        buildString {
            licenses.groupByCategory().forEach { (category, licenses) ->
                appendLine("$category (${licenses.size}):")
                appendLine()

                licenses.sortedBy { it.id.toString() }.forEach { license ->
                    appendLine("  ${license.description(category)}")
                }
                appendLine()
            }
        }

    private fun LicenseConfiguration.licensesList(): String =
        buildString {
            licenses.sortedBy { it.id.toString() }.forEach { license ->
                appendLine(license.description())
            }
        }

    private fun License.description(ignoreCategory: String? = null): String {
        val categories = sets.toMutableList().apply {
            if (includeInNoticeFile) {
                add("include-in-notices")
            }

            if (includeSourceCodeOfferInNoticeFile) {
                add("include-source-code-offer-in-notices")
            }

            ignoreCategory?.let { remove(it) }
        }

        return buildString {
            append(id)

            if (categories.isNotEmpty()) {
                append(": [${categories.joinToString()}]")
            }
        }
    }

    private fun Collection<License>.groupByCategory(): Map<String, List<License>> =
        flatMap { license ->
            license.sets.map { category -> license to category }
        }.groupBy({ it.second }, { it.first })
}
