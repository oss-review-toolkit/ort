/*
 * Copyright (C) 2022 Bosch.IO GmbH
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

package org.ossreviewtoolkit.clients.fossid.model.report

/**
 * Available FossID report types.
 */
enum class ReportType(private val value: String) {
    /**
     * Interactive and self-contained HTML report providing advanced features for searching, filtering and investigating
     * the results. Requires Javascript and corresponds to the UI report "Full FossID report".
     */
    HTML_DYNAMIC("dynamic"),

    /**
     * Static HTML page with a summary of identified components as well as vulnerability and dependency information.
     * Does not require Javascript and corresponds to the UI report "Basic Report".
     */
    HTML_STATIC("html"),

    /**
     * Software Package Data Exchange (SPDX) conformant XML file. Corresponds to the UI report "SPDX".
     */
    SPDX_RDF("spdx"),

    /**
     * Excel file with identified components, vulnerabilities, licenses, identification details and dependency analysis
     * results. Corresponds to the UI report "Excel sheet".
     */
    XLSX("xlsx");

    override fun toString(): String = value
}

/**
 * License types to be included in report.
 */
enum class SelectionType {
    /**
     * Include all licenses in the report.
     */
    INCLUDE_ALL_LICENSES,

    /**
     * Include licenses marked as copyleft only.
     */
    INCLUDE_COPYLEFT,

    /**
     * Include licenses marked as FOSS only.
     */
    INCLUDE_FOSS,

    /**
     * Include licenses marked for report inclusion only.
     */
    INCLUDE_MARKED_LICENSES
}
