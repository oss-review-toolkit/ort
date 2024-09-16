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

package org.ossreviewtoolkit.helper.commands.classifications

import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.helper.utils.OrtHelperCommand
import org.ossreviewtoolkit.model.licenses.LicenseClassifications
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.utils.common.expandTilde

internal class FilterCommand : OrtHelperCommand(
    help = "Filter license classifications from a given license classifications file."
) {
    private val licenseClassificationsFile by option(
        "--license-classifications-file",
        help = "The license classifications file to apply the filtering to."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val keepLicenseCategories by option(
        "--keep-categories", "-k",
        help = "The comma separated license categories for which the corresponding categorization shall be kept. All " +
            "categories corresponding to the kept categorizations will also be kept."
    ).convert { it.expandTilde() }.split(",").required()

    override fun run() {
        val classifications = licenseClassificationsFile.readValue<LicenseClassifications>()

        val categorizations = classifications.categorizations.filter { categorization ->
            categorization.categories.any { it in keepLicenseCategories }
        }

        val categories = categorizations.flatMapTo(mutableSetOf()) { it.categories }.map { categoryName ->
            classifications.categories.single { it.name == categoryName }
        }

        val result = LicenseClassifications(
            categories.sortedBy { it.name },
            categorizations.sortedBy { it.id.toString() }
        )

        licenseClassificationsFile.writeText(result.toYaml())
    }
}
