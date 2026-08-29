/*
 * Copyright (C) 2021 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.advisors.vulnerablecode

import io.kotest.core.TestConfiguration

import org.ossreviewtoolkit.model.AdvisorDetails
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.utils.toPurl
import org.ossreviewtoolkit.utils.test.readResourceValue

internal const val ADVISOR_NAME = "VulnerableCode"

internal val idLang = Identifier("Maven:org.apache.commons:commons-lang3:3.5")
internal val idText = Identifier("Maven:org.apache.commons:commons-text:1.1")
internal val idStruts = Identifier("Maven:org.apache.struts:struts2-assembly:2.5.14.1")
internal val idJUnit = Identifier("Maven:junit:junit:4.12")
internal val idHamcrest = Identifier("Maven:org.hamcrest:hamcrest-core:1.3")
internal val idLog4j = Identifier("Maven:org.apache.logging.log4j:log4j-core:2.17.0")

/**
 * The list with the identifiers of packages that are referenced in the test result file.
 */
internal val packageIdentifiers = setOf(idJUnit, idLang, idText, idStruts, idHamcrest)

/**
 * The list of packages referenced by the test result. These packages should be requested by the vulnerability provider.
 */
internal val packages = packageIdentifiers.map { it.toPurl() }

/**
 * Return a list with [Package]s from the analyzer result file that serve as input for the [VulnerableCode] advisor.
 */
internal fun TestConfiguration.inputPackagesFromAnalyzerResult(): Set<Package> =
    readResourceValue<OrtResult>("/ort-analyzer-result.yml").getPackages().mapTo(mutableSetOf()) { it.metadata }

/**
 * Return a set with [Package]s to be used as input for the [VulnerableCode] advisor derived from the given
 * [identifiers].
 */
internal fun inputPackagesFromIdentifiers(vararg identifiers: Identifier): Set<Package> =
    identifiers.mapTo(mutableSetOf()) { Package.EMPTY.copy(id = it, purl = it.toPurl()) }

/**
 * Generate the JSON body of the request to query information about the packages identified by the given [purls].
 * The request mainly consists of an array with the package URLs.
 */
internal fun generatePackagesRequest(purls: Collection<String> = packages): String =
    purls.joinToString(prefix = "{ \"purls\": [", postfix = "] }") { "\"$it\"" }

/**
 * Generate the JSON body of the request to query vulnerability information about the [Package] with the given [id].
 */
internal fun generatePackagesRequest(id: Identifier): String = generatePackagesRequest(listOf(id.toPurl()))

/**
 * Generate the JSON body of the API v3 packages request for the given [purls].
 */
internal fun generateV3PackagesRequest(purls: Collection<String> = packages): String =
    purls.joinToString(prefix = "{ \"purls\": [", postfix = "], \"details\": true }") { "\"$it\"" }

/**
 * Generate the JSON body of the API v3 packages request for the [Package] with the given [id].
 */
internal fun generateV3PackagesRequest(id: Identifier): String = generateV3PackagesRequest(listOf(id.toPurl()))

/**
 * Generate the JSON body of the API v3 advisories request for the given [purls].
 */
internal fun generateAdvisoriesRequest(purls: Collection<String> = packages): String =
    purls.joinToString(prefix = "{ \"purls\": [", postfix = "] }") { "\"$it\"" }

/**
 * Generate the JSON body of the API v3 advisories request for the [Package] with the given [id].
 */
internal fun generateAdvisoriesRequest(id: Identifier): String = generateAdvisoriesRequest(listOf(id.toPurl()))

/**
 * The advisor details expected in results produced by the VulnerableCode test instances.
 */
internal val details = AdvisorDetails(VulnerableCodeFactory.descriptor.id)
