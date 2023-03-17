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
import org.ossreviewtoolkit.model.config.ScannerConfiguration

import org.semver4j.Semver
import org.semver4j.Semver.VersionDiff

/**
 * Definition of a predicate to check whether the configuration of a scanner is compatible with the requirements
 * specified by a [ScannerCriteria].
 *
 * When testing whether a scan result is compatible with specific criteria this function is invoked on the
 * scanner configuration data stored in the result. By having different, scanner-specific matcher functions, this
 * compatibility check can be made very flexible.
 *
 * TODO: Switch to a more advanced type than String to represent the scanner configuration.
 */
typealias ScannerConfigMatcher = (String) -> Boolean

/**
 * A data class defining selection criteria for scanners.
 *
 * An instance of this class is passed to a [ScanResultsStorage] to define the criteria a scan result must match,
 * so that it can be used as a replacement for a result produced by an actual scanner. A scanner implementation
 * creates a [ScannerCriteria] with its exact properties. Users can override some or all of these properties to
 * state the criteria under which results from a storage are acceptable even if they deviate from the exact
 * properties of the scanner. That way it can be configured for instance, that results produced by an older
 * version of the scanner can be used.
 */
data class ScannerCriteria(
    /**
     * Criterion to match the scanner name. This string is interpreted as a regular expression. In the most basic
     * form, it can be an exact scanner name, but by using features of regular expressions, a more advanced
     * matching can be achieved. So it is possible for instance to select multiple scanners using an alternative ('|')
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
     * A function to check whether the configuration of a scanner is compatible with this [ScannerCriteria].
     */
    val configMatcher: ScannerConfigMatcher
) {
    companion object {
        /**
         * A matcher for scanner configurations that accepts all configurations passed in. This function can be
         * used if the concrete configuration of a scanner is irrelevant.
         */
        val ALL_CONFIG_MATCHER: ScannerConfigMatcher = { true }

        /**
         * The name of the property defining the regular expression for the scanner name as part of [ScannerCriteria].
         * The [scanner details][ScannerDetails] of the corresponding scanner must match the criteria.
         */
        const val PROP_CRITERIA_NAME = "regScannerName"

        /**
         * The name of the property defining the minimum version of the scanner as part of [ScannerCriteria]. The
         * [scanner details][ScannerDetails] of the corresponding scanner must match the criteria.
         */
        const val PROP_CRITERIA_MIN_VERSION = "minVersion"

        /**
         * The name of the property defining the maximum version of the scanner as part of [ScannerCriteria]. The
         * [scanner details][ScannerDetails] of the corresponding scanner must match the criteria.
         */
        const val PROP_CRITERIA_MAX_VERSION = "maxVersion"

        /**
         * A matcher for scanner configurations that accepts only exact matches of the [originalConfig]. This
         * function can be used by scanners that are extremely sensitive about their configuration.
         */
        fun exactConfigMatcher(originalConfig: String): ScannerConfigMatcher = { config -> originalConfig == config }

        /**
         * Generate a [ScannerCriteria] instance that is compatible with the given [details] and versions that differ
         * only in the provided [versionDiff].
         */
        fun forDetails(
            details: ScannerDetails,
            versionDiff: VersionDiff = VersionDiff.NONE
        ): ScannerCriteria {
            val minVersion = Semver(details.version)

            val maxVersion = when (versionDiff) {
                VersionDiff.NONE, VersionDiff.PRE_RELEASE, VersionDiff.BUILD -> minVersion.nextPatch()
                VersionDiff.PATCH -> minVersion.nextMinor()
                VersionDiff.MINOR -> minVersion.nextMajor()
                else -> throw IllegalArgumentException("Invalid version difference $versionDiff")
            }

            return ScannerCriteria(
                regScannerName = details.name,
                minVersion = minVersion,
                maxVersion = maxVersion,
                configMatcher = exactConfigMatcher(details.configuration)
            )
        }

        /**
         * Return a [ScannerCriteria] instance that is to be used when looking up existing scan results from a
         * [ScanResultsStorage]. By default, the properties of this instance are initialized to match the scanner
         * [details]. These default can be overridden by the [ScannerConfiguration.options] property in the provided
         * [config]: Use properties of the form `scannerName.property`, where `scannerName` is the name of the scanner
         * the configuration applies to, and `property` is the name of a property of the [ScannerCriteria] class. For
         * instance, to specify that a specific minimum version of ScanCode is allowed, set this property:
         * `options.ScanCode.minVersion=3.0.2`.
         */
        fun fromConfig(details: ScannerDetails, config: ScannerConfiguration): ScannerCriteria {
            val options = config.options?.get(details.name).orEmpty()
            val scannerVersion = Semver(normalizeVersion(details.version))
            val minVersion = parseVersion(options[PROP_CRITERIA_MIN_VERSION]) ?: scannerVersion
            val maxVersion = parseVersion(options[PROP_CRITERIA_MAX_VERSION]) ?: minVersion.nextMinor()
            val name = options[PROP_CRITERIA_NAME] ?: details.name

            return ScannerCriteria(name, minVersion, maxVersion, exactConfigMatcher(details.configuration))
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
     * Check whether the specified [details] match this [ScannerCriteria]. Return true if and only if the result
     * described by the [details] fulfills all the requirements expressed by the properties of this instance.
     */
    fun matches(details: ScannerDetails): Boolean {
        if (!nameRegex.matches(details.name)) return false

        val version = Semver(details.version)
        return minVersion <= version && version < maxVersion && configMatcher(details.configuration)
    }
}

/**
 * Parse the given [versionStr] to a [Semver] while trying to be failure tolerant.
 */
private fun parseVersion(versionStr: String?): Semver? =
    versionStr?.let { Semver(normalizeVersion(it)) }

/**
 * Normalize the given [versionStr] to make sure that it can be parsed to a [Semver]. The [Semver] class
 * requires that all components of a semantic version number are present. This function enables a more lenient
 * style when declaring a version. So for instance, the user can just write "2", and this gets expanded to
 * "2.0.0".
 */
private fun normalizeVersion(versionStr: String): String =
    versionStr.takeIf { v -> v.count { it == '.' } >= 2 } ?: normalizeVersion("$versionStr.0")
