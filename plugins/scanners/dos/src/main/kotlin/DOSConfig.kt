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
    /** The URL where the DOS service is running. */
    val serverUrl: String,

    /** Interval (in seconds) to use for polling scanjob status from DOS API. */
    val pollInterval: Int
) {
    companion object: Logging {
        /** Name of the configuration property for the server URL. */
        private const val SERVER_URL_PROPERTY = "serverUrl"

        /** Name of the configuration property for the polling interval. */
        private const val POLLING_INTERVAL_PROPERTY = "pollInterval"

        private const val DEFAULT_SERVER_URL = "https://double-open-server.herokuapp.com/api/"
        private const val DEFAULT_POLLING_INTERVAL = 5

        fun create(scannerConfig: ScannerConfiguration): DOSConfig {
            val dosScannerOptions = scannerConfig.options?.get("DOS")

            //requireNotNull(DOSScannerOptions) { "No DOS Scanner configuration found." }

            val serverUrl = dosScannerOptions?.get(SERVER_URL_PROPERTY) ?: DEFAULT_SERVER_URL
            val pollInterval = dosScannerOptions?.get(POLLING_INTERVAL_PROPERTY)?.toInt() ?: DEFAULT_POLLING_INTERVAL

            require(pollInterval >= DEFAULT_POLLING_INTERVAL) {
                "Polling interval must be >= $DEFAULT_POLLING_INTERVAL, current value is $pollInterval"
            }

            return DOSConfig(
                serverUrl,
                pollInterval
            )
        }
    }
}
