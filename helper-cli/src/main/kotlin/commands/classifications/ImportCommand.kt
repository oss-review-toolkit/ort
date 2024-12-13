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

package org.ossreviewtoolkit.helper.commands.classifications

import com.fasterxml.jackson.module.kotlin.readValue

import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.optionalValueLazy
import com.github.ajalt.clikt.parameters.types.enum
import com.github.ajalt.clikt.parameters.types.file

import java.net.URI

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.helper.utils.OrtHelperCommand
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.model.licenses.LicenseCategorization
import org.ossreviewtoolkit.model.licenses.LicenseCategory
import org.ossreviewtoolkit.model.licenses.LicenseClassifications
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.spdx.SpdxSingleLicenseExpression

internal class ImportCommand : OrtHelperCommand(
    help = "Import license classifications from supported providers to ORT format."
) {
    private val provider by argument(
        help = "The name of the provider to import license classifications from. Must be one of " +
            "${LicenseClassificationProvider.entries.map { it.name }}."
    ).enum<LicenseClassificationProvider>()

    private val prefix by option(
        "--prefix", "-p",
        help = "A prefix to use for all category names declared by the provider's classifications."
    ).optionalValueLazy { provider.name }

    private val licenseClassificationsFile by option(
        "--output", "-o",
        help = "The file to write the provided license classifications to."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }

    override fun run() {
        val classifications = provider.getClassifications().run {
            prefix?.let { prefixCategoryNames(it) } ?: this
        }.sort()

        val yaml = classifications.toYaml()

        licenseClassificationsFile?.run {
            writeText(yaml)
        } ?: println(yaml)
    }
}

private data class EclipseLicenses(
    val meta: Map<String, String>,
    val approved: Map<String, String>,
    val restricted: Map<String, String>
)

private enum class LicenseClassificationProvider(val url: String) {
    DOUBLE_OPEN("https://github.com/doubleopen-project/policy-configuration/raw/main/license-classifications.yml") {
        override fun getClassifications(): LicenseClassifications = yamlMapper.readValue(URI(url).toURL())
    },
    ECLIPSE("https://www.eclipse.org/legal/licenses.json") {
        override fun getClassifications(): LicenseClassifications {
            val json = jsonMapper.readValue<EclipseLicenses>(URI(url).toURL())

            logger.info { "Importing Eclipse license classifications dated ${json.meta["updated"]}." }

            val approved = LicenseCategory("approved")
            val restricted = LicenseCategory("restricted")
            val categorizations = mutableListOf<LicenseCategorization>()

            json.approved.mapTo(categorizations) { (id, _) ->
                LicenseCategorization(SpdxSingleLicenseExpression.parse(id), setOf(approved.name))
            }

            json.restricted.mapTo(categorizations) { (id, _) ->
                LicenseCategorization(SpdxSingleLicenseExpression.parse(id), setOf(restricted.name))
            }

            return LicenseClassifications(
                categories = listOf(approved, restricted),
                categorizations = categorizations
            )
        }
    },
    LDB_COLLECTOR("https://github.com/maxhbr/LDBcollector/raw/generated/ort/license-classifications.yml") {
        override fun getClassifications(): LicenseClassifications = yamlMapper.readValue(URI(url).toURL())
    };

    abstract fun getClassifications(): LicenseClassifications
}
