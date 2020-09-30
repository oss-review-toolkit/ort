/*
 * Copyright (C) 2019 HERE Europe B.V.
 * Copyright (C) 2020-2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.model.config

import com.sksamuel.hoplite.ConfigLoader
import com.sksamuel.hoplite.ConfigResult
import com.sksamuel.hoplite.Node
import com.sksamuel.hoplite.PropertySource
import com.sksamuel.hoplite.fp.getOrElse
import com.sksamuel.hoplite.fp.valid
import com.sksamuel.hoplite.parsers.toNode

import java.io.File

import org.ossreviewtoolkit.utils.log

/**
 * The configuration model for all ORT components.
 */
data class OrtConfiguration(
    /**
     * The configuration of the analyzer.
     */
    val analyzer: AnalyzerConfiguration? = null,

    /**
     * The configuration of the scanner.
     */
    val scanner: ScannerConfiguration? = null,

    /**
     * The license file patterns.
     */
    val licenseFilePatterns: LicenseFilenamePatterns? = null,

    /**
     * The configuration of the advisors, using the advisor's name as the key.
     */
    val advisor: Map<String, AdvisorConfiguration>? = null
) {
    companion object {
        /**
         * Load the [OrtConfiguration]. The different sources are used with this priority:
         *
         * 1. [Command line arguments][args]
         * 2. [Configuration file][configFile]
         * 3. default.conf from resources
         *
         * The configuration file is optional and does not have to exist. However, if it exists, but does not
         * contain a valid configuration, an [IllegalArgumentException] is thrown.
         */
        fun load(args: Map<String, String> = emptyMap(), configFile: File): OrtConfiguration {
            if (configFile.isFile) {
                log.info { "Using ORT configuration file at '$configFile'." }
            }

            val result = ConfigLoader.Builder()
                .addSource(argumentsSource(args))
                .addSource(PropertySource.file(configFile, optional = true))
                .addSource(PropertySource.resource("/default.conf"))
                .build()
                .loadConfig<OrtConfigurationWrapper>()

            return result.map { it.ort }.getOrElse { failure ->
                if (configFile.isFile) {
                    throw IllegalArgumentException(
                        "Failed to load configuration from ${configFile.absolutePath}: ${failure.description()}"
                    )
                }

                if (args.keys.any { it.startsWith("ort.") }) {
                    throw java.lang.IllegalArgumentException(
                        "Failed to load configuration from arguments $args: ${failure.description()}"
                    )
                }

                OrtConfiguration()
            }
        }

        /**
         * Generate a [PropertySource] providing access to the [args] the user has passed on the command line.
         */
        private fun argumentsSource(args: Map<String, String>): PropertySource {
            val node = args.toProperties().toNode("arguments").valid()
            return object : PropertySource {
                override fun node(): ConfigResult<Node> = node
            }
        }
    }
}

/**
 * An internal wrapper class to hold an [OrtConfiguration]. This class is needed to correctly map the _ort_
 * prefix in configuration files when they are processed by the underlying configuration library.
 */
internal data class OrtConfigurationWrapper(
    val ort: OrtConfiguration
)
