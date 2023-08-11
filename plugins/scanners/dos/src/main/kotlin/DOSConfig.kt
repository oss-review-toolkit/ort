/*
 * SPDX-FileCopyrightText: 2023 HH Partners
 *
 * SPDX-License-Identifier: MIT
 */

package org.ossreviewtoolkit.plugins.scanners.dos

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.model.config.ScannerConfiguration

/**
 * This is the configuration class for DOS Scanner.
 */
internal data class DOSConfig(
    /** The URL where the DOS service is running. **/
    val serverUrl: String,

    /** The secret token to use with the DOS service **/
    val serverToken: String,

    /** Interval (in seconds) to use for polling scanjob status from DOS API. **/
    val pollInterval: Int,

    /** Backend REST messaging timeout **/
    val restTimeout: Int
) {
    companion object: Logging {
        /** Name of the configuration property for the server URL. **/
        const val SERVER_URL_PROPERTY = "serverUrl"

        /** Name of the configuration property for the polling interval. **/
        private const val POLLING_INTERVAL_PROPERTY = "pollInterval"

        /** Name of the configuration property for the REST timeout. **/
        private const val REST_TIMEOUT_PROPERTY = "restTimeout"

        private const val DEFAULT_SERVER_URL = "https://double-open-server.herokuapp.com/api/"
        private const val DEFAULT_POLLING_INTERVAL = 5
        private const val DEFAULT_REST_TIMEOUT = 60

        fun create(scannerConfig: ScannerConfiguration): DOSConfig {
            val dosScannerOptions = scannerConfig.options?.get("DOS")

            //requireNotNull(DOSScannerOptions) { "No DOS Scanner configuration found." }

            val serverUrl = dosScannerOptions?.get(SERVER_URL_PROPERTY) ?: DEFAULT_SERVER_URL

            val serverToken = System.getenv("SERVER_TOKEN") ?:
                throw IllegalStateException("Server token not set!")

            val pollInterval = dosScannerOptions?.get(POLLING_INTERVAL_PROPERTY)?.toInt() ?: DEFAULT_POLLING_INTERVAL

            require(pollInterval >= DEFAULT_POLLING_INTERVAL) {
                "Polling interval must be >= $DEFAULT_POLLING_INTERVAL, current value is $pollInterval"
            }

            val restTimeout = dosScannerOptions?.get(REST_TIMEOUT_PROPERTY)?.toInt() ?: DEFAULT_REST_TIMEOUT

            require(restTimeout >= DEFAULT_REST_TIMEOUT) {
                "REST timeout must be >= $DEFAULT_REST_TIMEOUT, current value is $restTimeout"
            }

            return DOSConfig(
                serverUrl,
                serverToken,
                pollInterval,
                restTimeout
            )
        }
    }
}
