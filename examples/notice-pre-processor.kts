/*
 * Copyright (C) 2019 HERE Europe B.V.
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

/**
 * Include in generated notices only those licenses which either
 * A) were not in excluded paths or scopes in the .ort.yml, or
 * B) where `include_in_notice_file` is set to false in licenses.yml.
 */
val licensesIncludedInNotices = licenseConfiguration
        .licenses
        .filter { it.includeInNoticeFile }
        .map { it.id }
        .toSet()

val licensesWithSourceCodeOffer = licenseConfiguration
        .licenses
        .filter { it.includeSourceCodeOfferInNoticeFile }
        .map { it.id }
        .toSet()

val allLicenses = findings.values.flatMap { it.keys.map { it.toSpdx() } }.toSet()

findings = findings.mapValues { (_, licenseFindingsMap) ->
    licenseFindingsMap.filter { (license, _) -> license.toSpdx() in licensesIncludedInNotices }.toSortedMap()
}

val includedLicenses = findings.values.flatMap { it.keys.map { it.toSpdx() } }.toSet()
val ignoredLicenses = (allLicenses - includedLicenses).toSortedSet(compareBy { it.toString() })

println("The following licenses are not added to the notice file because they are not configured to be included:\n" +
        "${ignoredLicenses.joinToString("\n")}")

/**
 * Replace the default notices headers from AbstractNoticeReporter with a custom one.
 */
val customHeader = """
    Custom Header for Licenses and Copyright Notices:
    ================================================================================

""".trimIndent()

headers = listOf(customHeader)

headerWithLicenses = "This software includes external packages and source code.\nThe applicable license information is listed below:\n"
headerWithoutLicenses = "This project contains no external packages, but may depend on such packages.\n"

/**
 * Appends a source code offer if include_source_code_offer_in_notice_file is set to true in licenses.yml.
 */
footers = mutableListOf<String>()

val requiresSourceCodeOffer = includedLicenses.any {
    it in licensesWithSourceCodeOffer
}

if (requiresSourceCodeOffer) {
    footers += """
        Custom source code offer - insert your own text in notice-pre-processor.kts.

    """.trimIndent()
}

/**
 * Appends timestamp when notices were generated.
 */
val currentLocalDateTime = java.time.LocalDateTime.now()
val formatter = java.time.format.DateTimeFormatter.ofPattern("LLL d, yyyy")
val timestamp = currentLocalDateTime.format(formatter)

footers += """
    Notices generated with OSS Review Toolkit on $timestamp.
""".trimIndent()
