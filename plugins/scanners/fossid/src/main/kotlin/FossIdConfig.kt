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

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.utils.common.Options

/**
 * A data class that holds the configuration options supported by the [FossId] scanner. An instance of this class is
 * created from the [Options] contained in a [ScannerConfiguration] object under the key _FossId_. It offers the
 * following configuration options:
 *
 * * **"options.serverUrl":** The URL of the FossID server.
 * * **"secrets.user":** The user to connect to the FossID server.
 * * **"secrets.apiKey":** The API key of the user which connects to the FossID server.
 * * **"options.waitForResult":** When set to false, ORT does not wait for repositories to be downloaded nor scans to be
 *   completed. As a consequence, scan results won't be available in ORT result.
 * * **"options.deltaScans":** If set, ORT will create delta scans. When only changes in a repository need to be
 *   scanned, delta scans reuse the identifications of the latest scan on this repository to reduce the amount of
 *   findings. If *deltaScans* is set and no scan exist yet, an initial scan called "origin" scan will be created.
 * * **"options.deltaScanLimit":** This setting can be used to limit the number of delta scans to keep for a given
 *   repository. So if another delta scan is created, older delta scans are deleted until this number is reached. If
 *   unspecified, no limit is enforced on the number of delta scans to keep. This property is evaluated only if
 *   *deltaScans* is enabled.
 * * **"options.detectLicenseDeclaration":** When set, the FossID scan is configured to automatically detect file
 *   license declarations.
 * * **"options.detectCopyrightStatements":** When set, the FossID scan is configured to automatically detect copyright
 *   statements.
 *
 * Naming conventions options. If they are not set, default naming conventions are used.
 * * **"options.namingProjectPattern":** A pattern for project names when projects are created on the FossID instance.
 *   Contains variables prefixed by "$" e.g. "$Var1_$Var2". Variables are also passed as options and are prefixed by
 *   [NAMING_CONVENTION_VARIABLE_PREFIX] e.g. namingVariableVar1 = "foo".
 * * **"options.namingScanPattern":** A pattern for scan names when scans are created on the FossID instance.
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
data class FossIdConfig(
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

    /** A limit on the amount of snippets to fetch. **/
    val snippetsLimit: Int,

    /** The sensitivity of the scan. */
    val sensitivity: Int,

    /** Stores the map with FossID-specific configuration options. */
    private val options: Map<String, String>
) {
    companion object {
        /** Name of the configuration property for the server URL. */
        private const val PROP_SERVER_URL = "serverUrl"

        /** Name of the configuration property for the username. */
        private const val PROP_USER = "user"

        /** Name of the configuration property for the API key. */
        private const val PROP_API_KEY = "apiKey"

        /** Name of the configuration property controlling whether ORT should wait for FossID results. */
        private const val PROP_WAIT_FOR_RESULT = "waitForResult"

        /** Name of the configuration property defining the naming convention for projects. */
        private const val PROP_NAMING_PROJECT_PATTERN = "namingProjectPattern"

        /** Name of the configuration property defining the naming convention for scans. */
        private const val PROP_NAMING_SCAN_PATTERN = "namingScanPattern"

        /** Name of the configuration property defining whether to keep failed scans. */
        private const val PROP_KEEP_FAILED_SCANS = "keepFailedScans"

        /** Name of the configuration property controlling whether delta scans are to be created. */
        private const val PROP_DELTA_SCAN = "deltaScans"

        /** Name of the configuration property that limits the number of delta scans. */
        private const val PROP_DELTA_SCAN_LIMIT = "deltaScanLimit"

        private const val PROP_DETECT_LICENSE_DECLARATIONS = "detectLicenseDeclarations"

        private const val PROP_DETECT_COPYRIGHT_STATEMENTS = "detectCopyrightStatements"

        /** Name of the configuration property defining the timeout in minutes for communication with FossID. */
        private const val PROP_TIMEOUT = "timeout"

        /** Name of the configuration property controlling whether matched lines of snippets are to be fetched. */
        private const val PROP_FETCH_SNIPPET_MATCHED_LINES = "fetchSnippetMatchedLines"

        /** Name of the configuration property defining the limit on the amount of snippets to fetch. */
        private const val PROP_SNIPPETS_LIMIT = "snippetsLimit"

        /** Name of the configuration property defining the sensitivity of the scan. */
        private const val PROP_SENSITIVITY = "sensitivity"

        /**
         * The scanner options beginning with this prefix will be used to parameterize project and scan names.
         */
        private const val NAMING_CONVENTION_VARIABLE_PREFIX = "namingVariable"

        /**
         * Default timeout in minutes for communication with FossID.
         */
        @JvmStatic
        private val DEFAULT_TIMEOUT = 60

        /**
         * Default limit on the amount of snippets to fetch.
         */
        @JvmStatic
        private val DEFAULT_SNIPPETS_LIMIT = 500

        /**
         * Default scan sensitivity.
         */
        @JvmStatic
        private val DEFAULT_SENSITIVITY = 10

        fun create(options: Options, secrets: Options): FossIdConfig {
            require(options.isNotEmpty()) { "No FossID Scanner configuration found." }

            val serverUrl = options[PROP_SERVER_URL]
                ?: throw IllegalArgumentException("No FossID server URL configuration found.")
            val user = secrets[PROP_USER]
                ?: throw IllegalArgumentException("No FossID User configuration found.")
            val apiKey = secrets[PROP_API_KEY]
                ?: throw IllegalArgumentException("No FossID API Key configuration found.")

            val waitForResult = options[PROP_WAIT_FOR_RESULT]?.toBoolean() != false

            val keepFailedScans = options[PROP_KEEP_FAILED_SCANS]?.toBoolean() == true
            val deltaScans = options[PROP_DELTA_SCAN]?.toBoolean() == true
            val deltaScanLimit = options[PROP_DELTA_SCAN_LIMIT]?.toInt() ?: Int.MAX_VALUE

            val detectLicenseDeclarations = options[PROP_DETECT_LICENSE_DECLARATIONS]?.toBoolean() == true
            val detectCopyrightStatements = options[PROP_DETECT_COPYRIGHT_STATEMENTS]?.toBoolean() == true

            val timeout = options[PROP_TIMEOUT]?.toInt() ?: DEFAULT_TIMEOUT

            val fetchSnippetMatchedLines = options[PROP_FETCH_SNIPPET_MATCHED_LINES]?.toBoolean() == true
            val snippetsLimit = options[PROP_SNIPPETS_LIMIT]?.toInt() ?: DEFAULT_SNIPPETS_LIMIT

            val sensitivity = options[PROP_SENSITIVITY]?.toInt() ?: DEFAULT_SENSITIVITY

            require(deltaScanLimit > 0) {
                "deltaScanLimit must be > 0, current value is $deltaScanLimit."
            }

            require(sensitivity in 0..20) {
                "Sensitivity must be between 0 and 20, current value is $sensitivity."
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
                options = options,
                snippetsLimit = snippetsLimit,
                sensitivity = sensitivity
            )
        }
    }

    /**
     * Create a [FossIdNamingProvider] helper object based on the configuration stored in this object.
     */
    fun createNamingProvider(): FossIdNamingProvider {
        val namingProjectPattern = options[PROP_NAMING_PROJECT_PATTERN]?.also {
            logger.info { "Naming pattern for projects is $it." }
        }

        val namingScanPattern = options[PROP_NAMING_SCAN_PATTERN]?.also {
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
