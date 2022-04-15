/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.scanner.scanners.fossid

import java.time.Duration

import org.ossreviewtoolkit.clients.fossid.FossIdRestService
import org.ossreviewtoolkit.clients.fossid.FossIdServiceWithVersion
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.utils.core.OkHttpClientHelper
import org.ossreviewtoolkit.utils.core.log

/**
 * A data class that holds the configuration options supported by the [FossId] scanner. An instance of this class is
 * created from the options contained in a [ScannerConfiguration] object under the key _FossId_. It offers the
 * following configuration options:
 *
 * * **"serverUrl":** The URL of the FossID server.
 * * **"user":** The user to connect to the FossID server.
 * * **"apiKey":** The API key of the user which connects to the FossID server.
 * * **"packageNamespaceFilter":** If this optional filter is set, only packages having an identifier in given namespace
 * will be scanned.
 * * **"packageAuthorsFilter":** If this optional filter is set, only packages from a given author will be scanned.
 * * **"addAuthenticationToUrl":** When set, ORT will add credentials from its Authenticator to the URLs sent to FossID.
 * * **"waitForResult":** When set to false, ORT doesn't wait for repositories to be downloaded nor scans to be
 * completed. As a consequence, scan results won't be available in ORT result.
 * * **"deltaScans":** When set, ORT will create delta scans. When only changes in a repository need to be scanned,
 * delta scans reuse the identifications of latest scan on this repository to reduce the amount of findings. If
 * deltaScans is set and no scan exist yet, an initial scan called "origin" scan will be created.
 * * **"deltaScanLimit":** This setting can be used to limit the number of delta scans to keep for a given repository.
 * So if another delta scan is created, older delta scans are deleted until this number is reached. If unspecified, no
 * limit is enforced on the number of delta scans to keep. This property is evaluated only if *deltaScans* is enabled.
 *
 * Naming conventions options. If they are not set, default naming convention are used.
 * * **"namingProjectPattern":** A pattern for project names when projects are created on the FossID instance. Contains
 * variables prefixed by "$" e.g. "$Var1_$Var2". Variables are also passed as options and are prefixed by
 * [NAMING_CONVENTION_VARIABLE_PREFIX] e.g. namingVariableVar1 = "foo".
 * * **"namingScanPattern":** A pattern for scan names when scans are created on the FossID instance.
 */
internal data class FossIdConfig(
    /** The URL where the FossID service is running. */
    val serverUrl: String,

    /** The API key to access the FossID server. */
    val apiKey: String,

    /** The user to authenticate against the server. */
    val user: String,

    /** Flag whether the scanner should wait for the completion of FossID scans. */
    val waitForResult: Boolean,

    /** Filter for package namespaces (empty string if undefined). */
    val packageNamespaceFilter: String,

    /** Filter for package authors (empty string if undefined). */
    val packageAuthorsFilter: String,

    /** Flag whether credentials should be passed to FossID in URLs. */
    val addAuthenticationToUrl: Boolean,

    /** Flag whether delta scans should be triggered. */
    val deltaScans: Boolean,

    /** A maximum number of delta scans to keep for a single repository. */
    val deltaScanLimit: Int,

    /** Timeout in minutes for communication with FossID. */
    val timeout: Int,

    /** Stores the map with FossID-specific configuration options. */
    private val options: Map<String, String>
) {
    companion object {
        /** Name of the configuration property for the server URL. */
        private const val SERVER_URL_PROPERTY = "serverUrl"

        /** Name of the configuration property for the API key. */
        private const val API_KEY_PROPERTY = "apiKey"

        /** Name of the configuration property for the user name. */
        private const val USER_PROPERTY = "user"

        /** Name of the configuration property for the packages namespace filter. */
        private const val NAMESPACE_FILTER_PROPERTY = "packageNamespaceFilter"

        /** Name of the configuration property for the packages authros filter. */
        private const val AUTHORS_FILTER_PROPERTY = "packageAuthorsFilter"

        /** Name of the configuration property controlling that credentials are added to URLs. */
        private const val CREDENTIALS_IN_URL_PROPERTY = "addAuthenticationToUrl"

        /** Name of the configuration property controlling whether ORT should wait for FossID results. */
        private const val WAIT_FOR_RESULT_PROPERTY = "waitForResult"

        /** Name of the configuration property defining the naming convention for projects. */
        private const val NAMING_PROJECT_PATTERN_PROPERTY = "namingProjectPattern"

        /** Name of the configuration property defining the naming convention for scans. */
        private const val NAMING_SCAN_PATTERN_PROPERTY = "namingScanPattern"

        /** Name of the configuration property controlling whether delta scans are to be created. */
        private const val DELTA_SCAN_PROPERTY = "deltaScans"

        /** Name of the configuration property that limits the number of delta scans. */
        private const val DELTA_SCAN_LIMIT_PROPERTY = "deltaScanLimit"

        /** Name of the configuration property defining the timeout in minutes for communication with FossID. */
        private const val TIMEOUT = "timeout"

        /**
         * The scanner options beginning with this prefix will be used to parametrize project and scan names.
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
            val apiKey = fossIdScannerOptions[API_KEY_PROPERTY]
                ?: throw IllegalArgumentException("No FossID API Key configuration found.")
            val user = fossIdScannerOptions[USER_PROPERTY]
                ?: throw IllegalArgumentException("No FossID User configuration found.")
            val packageNamespaceFilter = fossIdScannerOptions[NAMESPACE_FILTER_PROPERTY].orEmpty()
            val packageAuthorsFilter = fossIdScannerOptions[AUTHORS_FILTER_PROPERTY].orEmpty()
            val addAuthenticationToUrl = fossIdScannerOptions[CREDENTIALS_IN_URL_PROPERTY]?.toBoolean() ?: false
            val waitForResult = fossIdScannerOptions[WAIT_FOR_RESULT_PROPERTY]?.toBoolean() ?: true
            val deltaScans = fossIdScannerOptions[DELTA_SCAN_PROPERTY]?.toBoolean() ?: false

            val deltaScanLimit = fossIdScannerOptions[DELTA_SCAN_LIMIT_PROPERTY]?.toInt() ?: Int.MAX_VALUE

            val timeout = fossIdScannerOptions[TIMEOUT]?.toInt() ?: DEFAULT_TIMEOUT

            require(deltaScanLimit > 0) {
                "deltaScanLimit must be > 0, current value is $deltaScanLimit."
            }

            log.info { "waitForResult parameter is set to '$waitForResult'" }

            return FossIdConfig(
                serverUrl,
                apiKey,
                user,
                waitForResult,
                packageNamespaceFilter,
                packageAuthorsFilter,
                addAuthenticationToUrl,
                deltaScans,
                deltaScanLimit,
                timeout,
                fossIdScannerOptions
            )
        }
    }

    /**
     * Create a [FossIdNamingProvider] helper object based on the configuration stored in this object.
     */
    fun createNamingProvider(): FossIdNamingProvider {
        val namingProjectPattern = options[NAMING_PROJECT_PATTERN_PROPERTY]?.also {
            log.info { "Naming pattern for projects is $it." }
        }

        val namingScanPattern = options[NAMING_SCAN_PATTERN_PROPERTY]?.also {
            log.info { "Naming pattern for scans is $it." }
        }

        val namingConventionVariables = options
            .filterKeys { it.startsWith(NAMING_CONVENTION_VARIABLE_PREFIX) }
            .mapKeys { it.key.substringAfter(NAMING_CONVENTION_VARIABLE_PREFIX) }

        return FossIdNamingProvider(namingProjectPattern, namingScanPattern, namingConventionVariables)
    }

    /**
     * Create the [FossIdServiceWithVersion] to interact with the FossID instance described by this configuration.
     */
    fun createService(): FossIdServiceWithVersion {
        log.info { "The FossID server URL is $serverUrl." }

        val service = FossIdRestService.create(
            serverUrl,
            OkHttpClientHelper.buildClient {
                readTimeout(Duration.ofSeconds(60))
            }
        )

        return FossIdServiceWithVersion.instance(service).also {
            if (it.version.isEmpty()) {
                log.warn { "The FossID server is running an unknown version." }
            } else {
                log.info { "The FossID server is running version ${it.version}." }
            }
        }
    }
}
