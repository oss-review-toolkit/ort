/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.scanners.fossid

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.model.config.ScannerConfiguration

/**
 * A data class that holds the configuration options supported by the [FossId] scanner. An instance of this class is
 * created from the options contained in a [ScannerConfiguration] object under the key _FossId_. It offers the
 * following configuration options:
 *
 * * **"serverUrl":** The URL of the FossID server.
 * * **"user":** The user to connect to the FossID server.
 * * **"apiKey":** The API key of the user which connects to the FossID server.
 * * **"waitForResult":** When set to false, ORT does not wait for repositories to be downloaded nor scans to be
 *   completed. As a consequence, scan results won't be available in ORT result.
 * * **"deltaScans":** If set, ORT will create delta scans. When only changes in a repository need to be scanned,
 *   delta scans reuse the identifications of the latest scan on this repository to reduce the amount of findings. If
 *   *deltaScans* is set and no scan exist yet, an initial scan called "origin" scan will be created.
 * * **"deltaScanLimit":** This setting can be used to limit the number of delta scans to keep for a given repository.
 *   So if another delta scan is created, older delta scans are deleted until this number is reached. If unspecified, no
 *   limit is enforced on the number of delta scans to keep. This property is evaluated only if *deltaScans* is enabled.
 * * **"detectLicenseDeclaration":** When set, the FossID scan is configured to automatically detect file license
 *   declarations.
 * * **"detectCopyrightStatements":** When set, the FossID scan is configured to automatically detect copyright
 *   statements.
 *
 * Naming conventions options. If they are not set, default naming conventions are used.
 * * **"namingProjectPattern":** A pattern for project names when projects are created on the FossID instance. Contains
 *   variables prefixed by "$" e.g. "$Var1_$Var2". Variables are also passed as options and are prefixed by
 *   [NAMING_CONVENTION_VARIABLE_PREFIX] e.g. namingVariableVar1 = "foo".
 * * **"namingScanPattern":** A pattern for scan names when scans are created on the FossID instance.
 *
 * URL mapping options. These options allow transforming the URLs of specific repositories before they are passed to
 * the FossID service. This may be necessary if FossID uses a different mechanism to clone a repository, e.g. via SSH
 * instead of HTTP. Options of this form start with the prefix [FossIdUrlProvider.PREFIX_URL_MAPPING] followed by an
 * arbitrary name. Their values define the mapping to be applied consisting of two parts separated by the string
 * " -> ":
 * * A regular expression to match the repository URL.
 * * The replacement to be used for this repository URL. It can access the capture groups defined by the regular
 * expression, so that rather flexible transformations can be achieved. In addition, it can contain the variables
 * "#user" and "#password" that are replaced by the credentials known for the target host.
 *
 * The example
 *
 * `mapExampleRepo = https://my-repo.example.org(?<repoPath>.*) -> ssh://my-mapped-repo.example.org${repoPath}`
 *
 * would change the scheme from "https" to "ssh" and the host name for all repositories hosted on
 * "my-repo.example.org". With
 *
 * `mapAddCredentials =
 *   (?<scheme>)://(?<host>)(?<port>:\\d+)?(?<repoPath>.*) -> ${scheme}://#user:#password@${host}${port}${repoPath}`
 *
 * every repository URL would be added credentials. Mappings are applied in the order they are defined.
 */
internal data class FossIdConfig(
    /** The URL where the FossID service is running. */
    val serverUrl: String,

    /** The user to authenticate against the server. */
    val user: String,

    /** The API key to access the FossID server. */
    val apiKey: String,

    /** Flag whether the scanner should wait for the completion of FossID scans. */
    val waitForResult: Boolean,

    /** Flag whether failed scans should be kept. */
    val keepFailedScans: Boolean,

    /** Flag whether delta scans should be triggered. */
    val deltaScans: Boolean,

    /** A maximum number of delta scans to keep for a single repository. */
    val deltaScanLimit: Int,

    /**
     * Configure to automatically detect license declarations. Uses the `auto_identification_detect_copyright` setting.
     */
    val detectLicenseDeclarations: Boolean,

    /** Configure to detect copyright statements. Uses the `auto_identification_detect_copyright` setting. */
    val detectCopyrightStatements: Boolean,

    /** Timeout in minutes for communication with FossID. */
    val timeout: Int,

    /** Whether matched lines of snippets are to be fetched. */
    val fetchSnippetMatchedLines: Boolean,

    /** Stores the map with FossID-specific configuration options. */
    private val options: Map<String, String>
) {
    companion object : Logging {
        /** Name of the configuration property for the server URL. */
        private const val SERVER_URL_PROPERTY = "serverUrl"

        /** Name of the configuration property for the username. */
        private const val USER_PROPERTY = "user"

        /** Name of the configuration property for the API key. */
        private const val API_KEY_PROPERTY = "apiKey"

        /** Name of the configuration property controlling whether ORT should wait for FossID results. */
        private const val WAIT_FOR_RESULT_PROPERTY = "waitForResult"

        /** Name of the configuration property defining the naming convention for projects. */
        private const val NAMING_PROJECT_PATTERN_PROPERTY = "namingProjectPattern"

        /** Name of the configuration property defining the naming convention for scans. */
        private const val NAMING_SCAN_PATTERN_PROPERTY = "namingScanPattern"

        /** Name of the configuration property defining whether to keep failed scans. */
        private const val KEEP_FAILED_SCANS_PROPERTY = "keepFailedScans"

        /** Name of the configuration property controlling whether delta scans are to be created. */
        private const val DELTA_SCAN_PROPERTY = "deltaScans"

        /** Name of the configuration property that limits the number of delta scans. */
        private const val DELTA_SCAN_LIMIT_PROPERTY = "deltaScanLimit"

        private const val DETECT_LICENSE_DECLARATIONS_PROPERTY = "detectLicenseDeclarations"

        private const val DETECT_COPYRIGHT_STATEMENTS_PROPERTY = "detectLicenseDeclarations"

        /** Name of the configuration property defining the timeout in minutes for communication with FossID. */
        private const val TIMEOUT = "timeout"

        /** Name of the configuration property controlling whether matched lines of snippets are to be fetched. */
        private const val FETCH_SNIPPET_MATCHED_LINES = "fetchSnippetMatchedLines"

        /**
         * The scanner options beginning with this prefix will be used to parameterize project and scan names.
         */
        private const val NAMING_CONVENTION_VARIABLE_PREFIX = "namingVariable"

        /**
         * Default timeout in minutes for communication with FossID.
         */
        @JvmStatic
        private val DEFAULT_TIMEOUT = 60

        fun create(scannerConfig: ScannerConfiguration): FossIdConfig {
            val fossIdScannerOptions = scannerConfig.options?.get("FossId")

            requireNotNull(fossIdScannerOptions) { "No FossID Scanner configuration found." }

            val serverUrl = fossIdScannerOptions[SERVER_URL_PROPERTY]
                ?: throw IllegalArgumentException("No FossID server URL configuration found.")
            val user = fossIdScannerOptions[USER_PROPERTY]
                ?: throw IllegalArgumentException("No FossID User configuration found.")
            val apiKey = fossIdScannerOptions[API_KEY_PROPERTY]
                ?: throw IllegalArgumentException("No FossID API Key configuration found.")

            val waitForResult = fossIdScannerOptions[WAIT_FOR_RESULT_PROPERTY]?.toBoolean() ?: true

            val keepFailedScans = fossIdScannerOptions[KEEP_FAILED_SCANS_PROPERTY]?.toBoolean() ?: false
            val deltaScans = fossIdScannerOptions[DELTA_SCAN_PROPERTY]?.toBoolean() ?: false
            val deltaScanLimit = fossIdScannerOptions[DELTA_SCAN_LIMIT_PROPERTY]?.toInt() ?: Int.MAX_VALUE

            val detectLicenseDeclarations =
                fossIdScannerOptions[DETECT_LICENSE_DECLARATIONS_PROPERTY]?.toBoolean() ?: false
            val detectCopyrightStatements =
                fossIdScannerOptions[DETECT_COPYRIGHT_STATEMENTS_PROPERTY]?.toBoolean() ?: false

            val timeout = fossIdScannerOptions[TIMEOUT]?.toInt() ?: DEFAULT_TIMEOUT

            val fetchSnippetMatchedLines = fossIdScannerOptions[FETCH_SNIPPET_MATCHED_LINES]?.toBoolean() ?: false

            require(deltaScanLimit > 0) {
                "deltaScanLimit must be > 0, current value is $deltaScanLimit."
            }

            logger.info { "waitForResult parameter is set to '$waitForResult'" }

            return FossIdConfig(
                serverUrl = serverUrl,
                user = user,
                apiKey = apiKey,
                waitForResult = waitForResult,
                keepFailedScans = keepFailedScans,
                deltaScans = deltaScans,
                deltaScanLimit = deltaScanLimit,
                detectLicenseDeclarations = detectLicenseDeclarations,
                detectCopyrightStatements = detectCopyrightStatements,
                timeout = timeout,
                fetchSnippetMatchedLines = fetchSnippetMatchedLines,
                options = fossIdScannerOptions
            )
        }
    }

    /**
     * Create a [FossIdNamingProvider] helper object based on the configuration stored in this object.
     */
    fun createNamingProvider(): FossIdNamingProvider {
        val namingProjectPattern = options[NAMING_PROJECT_PATTERN_PROPERTY]?.also {
            logger.info { "Naming pattern for projects is $it." }
        }

        val namingScanPattern = options[NAMING_SCAN_PATTERN_PROPERTY]?.also {
            logger.info { "Naming pattern for scans is $it." }
        }

        val namingConventionVariables = options
            .filterKeys { it.startsWith(NAMING_CONVENTION_VARIABLE_PREFIX) }
            .mapKeys { it.key.substringAfter(NAMING_CONVENTION_VARIABLE_PREFIX) }

        return FossIdNamingProvider(namingProjectPattern, namingScanPattern, namingConventionVariables)
    }

    /**
     * Create a [FossIdUrlProvider] helper object based on the configuration stored in this object.
     */
    fun createUrlProvider() = FossIdUrlProvider.create(options)
}
