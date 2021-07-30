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

import org.ossreviewtoolkit.clients.fossid.FossIdRestService
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.utils.OkHttpClientHelper
import org.ossreviewtoolkit.utils.log

import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory

/**
 * A data class that holds the configuration options supported by the [FossId] scanner. An instance of this class is
 * created from the options contained in a [ScannerConfiguration] object.
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

        /**
         * The scanner options beginning with this prefix will be used to parametrize project and scan names.
         */
        private const val NAMING_CONVENTION_VARIABLE_PREFIX = "namingVariable"

        fun create(scannerConfig: ScannerConfiguration): FossIdConfig {
            val fossIdScannerOptions = scannerConfig.options?.get("FossId")

            requireNotNull(fossIdScannerOptions) { "No FossId Scanner configuration found." }

            val serverUrl = fossIdScannerOptions[SERVER_URL_PROPERTY]
                ?: throw IllegalArgumentException("No FossId server URL configuration found.")
            val apiKey = fossIdScannerOptions[API_KEY_PROPERTY]
                ?: throw IllegalArgumentException("No FossId API Key configuration found.")
            val user = fossIdScannerOptions[USER_PROPERTY]
                ?: throw IllegalArgumentException("No FossId User configuration found.")
            val packageNamespaceFilter = fossIdScannerOptions[NAMESPACE_FILTER_PROPERTY].orEmpty()
            val packageAuthorsFilter = fossIdScannerOptions[AUTHORS_FILTER_PROPERTY].orEmpty()
            val addAuthenticationToUrl = fossIdScannerOptions[CREDENTIALS_IN_URL_PROPERTY].toBoolean()
            val waitForResult = fossIdScannerOptions[WAIT_FOR_RESULT_PROPERTY]?.toBooleanStrictOrNull() ?: true
            val deltaScans = fossIdScannerOptions[DELTA_SCAN_PROPERTY].toBoolean()

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
     * Create the [FossIdRestService] to interact with the FossID instance described by this configuration.
     */
    fun createService(): FossIdRestService {
        val client = OkHttpClientHelper.buildClient()
        log.info { "FossID server URL is $serverUrl." }

        val retrofit = Retrofit.Builder()
            .client(client)
            .baseUrl(serverUrl)
            .addConverterFactory(JacksonConverterFactory.create(FossIdRestService.JSON_MAPPER))
            .build()

        return retrofit.create(FossIdRestService::class.java)
    }
}
