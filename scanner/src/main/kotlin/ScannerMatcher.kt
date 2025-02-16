/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.scanner

import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.utils.common.Options

import org.semver4j.Semver

/**
 * A data class defining selection criteria for scanners.
 *
 * An instance of this class is passed to a [ScanStorageReader] to define the criteria a scan result must match,
 * so that it can be used as a replacement for a result produced by an actual scanner. A scanner implementation
 * creates a [ScannerMatcher] with its exact properties. Users can override some or all of these properties to
 * state the criteria under which results from a storage are acceptable even if they deviate from the exact
 * properties of the scanner. That way it can be configured, for instance, that results produced by an older
 * version of the scanner can be used.
 */
data class ScannerMatcher(
    /**
     * Criterion to match the scanner name. This string is interpreted as a regular expression. In the most basic
     * form, it can be an exact scanner name, but by using features of regular expressions, a more advanced
     * matching can be achieved. So it is possible, for instance, to select multiple scanners using an alternative ('|')
     * expression or an arbitrary one using a wildcard ('.*').
     */
    val regScannerName: String,

    /**
     * Criterion to match for the minimum scanner version. Results are accepted if they are produced from scanners
     * with at least this version.
     */
    val minVersion: Semver,

    /**
     * Criterion to match for the maximum scanner version. Results are accepted if they are produced from scanners
     * with a version lower than this one. (This bound of the version range is excluding.)
     */
    val maxVersion: Semver,

    /**
     * Criterion to match the [configuration][ScannerDetails.configuration] of the scanner. If `null`, all
     * configurations are matched.
     */
    val configuration: String?
) {
    companion object {
        /**
         * Return a [ScannerMatcher] instance that is to be used when looking up existing scan results from a
         * [ScanStorageReader]. By default, the properties of this instance are initialized to match the scanner
         * [details]. These defaults can be overridden by the provided [config].
         */
        fun create(details: ScannerDetails, config: ScannerMatcherConfig = ScannerMatcherConfig.EMPTY): ScannerMatcher {
            val scannerVersion = checkNotNull(Semver.coerce(details.version))
            val minVersion = Semver.coerce(config.minVersion) ?: scannerVersion
            val maxVersion = Semver.coerce(config.maxVersion) ?: minVersion.nextMinor()
            val name = config.regScannerName ?: details.name
            val configuration = config.configuration ?: details.configuration

            return ScannerMatcher(name, minVersion, maxVersion, configuration)
        }
    }

    /** The regular expression to match for the scanner name. */
    private val nameRegex: Regex by lazy { Regex(regScannerName) }

    init {
        require(minVersion < maxVersion) {
            "The `maxVersion` is exclusive and must be greater than the `minVersion`."
        }
    }

    /**
     * Check whether the specified [details] match this [ScannerMatcher]. Return true if and only if the result
     * described by the [details] fulfills all the requirements expressed by the properties of this instance.
     */
    fun matches(details: ScannerDetails): Boolean {
        if (!nameRegex.matches(details.name)) return false

        val version = Semver(details.version)
        return minVersion <= version && version < maxVersion &&
            (configuration == null || configuration == details.configuration)
    }
}

/**
 * A holder class for the [ScannerMatcher] configuration.
 */
data class ScannerMatcherConfig(
    val regScannerName: String? = null,
    val minVersion: String? = null,
    val maxVersion: String? = null,
    val configuration: String? = null
) {
    companion object {
        val EMPTY = ScannerMatcherConfig(null, null, null, null)

        /**
         * The name of the property defining the regular expression for the scanner name as part of [ScannerMatcher].
         * The [scanner details][ScannerDetails] of the corresponding scanner must match the criteria.
         */
        internal const val PROP_CRITERIA_NAME = "regScannerName"

        /**
         * The name of the property defining the minimum version of the scanner as part of [ScannerMatcher]. The
         * [scanner details][ScannerDetails] of the corresponding scanner must match the criteria.
         */
        internal const val PROP_CRITERIA_MIN_VERSION = "minVersion"

        /**
         * The name of the property defining the maximum version of the scanner as part of [ScannerMatcher]. The
         * [scanner details][ScannerDetails] of the corresponding scanner must match the criteria.
         */
        internal const val PROP_CRITERIA_MAX_VERSION = "maxVersion"

        /**
         * The name of the property defining the configuration of the scanner as part of [ScannerMatcher]. The
         * [scanner details][ScannerDetails] of the corresponding scanner must match the criteria.
         */
        internal const val PROP_CRITERIA_CONFIGURATION = "configuration"

        private val properties = listOf(
            PROP_CRITERIA_NAME, PROP_CRITERIA_MIN_VERSION, PROP_CRITERIA_MAX_VERSION, PROP_CRITERIA_CONFIGURATION
        )

        /**
         * Create a [ScannerMatcherConfig] from the provided options. Return the created config and the options without
         * the properties that are used to configure the matcher.
         */
        fun create(options: Options): Pair<ScannerMatcherConfig, Options> {
            val filteredOptions = options.filterKeys { it !in properties }

            val matcherConfig = ScannerMatcherConfig(
                regScannerName = options[PROP_CRITERIA_NAME],
                minVersion = options[PROP_CRITERIA_MIN_VERSION],
                maxVersion = options[PROP_CRITERIA_MAX_VERSION],
                configuration = options[PROP_CRITERIA_CONFIGURATION]
            )

            return matcherConfig to filteredOptions
        }
    }
}
