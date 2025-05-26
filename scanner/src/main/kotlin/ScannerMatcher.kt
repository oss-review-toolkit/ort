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
     * Criterion to match the scanner version, including this minimum version. Results are accepted if they are produced
     * by scanners with a version greater than or equal to this version.
     */
    val minVersion: Semver,

    /**
     * Criterion to match the scanner version, excluding this maximum version. Results are accepted if they are produced
     * by scanners with a version less than this version.
     */
    val maxVersion: Semver,

    /**
     * Criterion to match the [configuration][ScannerDetails.configuration] of the scanner.
     */
    val configuration: String
) {
    companion object {
        /**
         * Return a [ScannerMatcher] instance that is to be used when looking up existing scan results from a
         * [ScanStorageReader]. By default, the properties of this instance are initialized to match the scanner
         * [details]. These defaults can be overridden by the provided [criteria].
         */
        fun create(details: ScannerDetails, criteria: ScannerMatcherCriteria? = null): ScannerMatcher {
            val scannerVersion = checkNotNull(Semver.coerce(details.version))
            val minVersion = Semver.coerce(criteria?.minVersion) ?: scannerVersion
            val maxVersion = Semver.coerce(criteria?.maxVersion) ?: minVersion.nextMajor()
            val name = criteria?.regScannerName ?: details.name
            val configuration = criteria?.configuration ?: details.configuration

            return ScannerMatcher(name, minVersion, maxVersion, configuration)
        }
    }

    /** The regular expression to match for the scanner name. */
    private val nameRegex by lazy { Regex(regScannerName) }

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
        return version in minVersion..<maxVersion && configuration == details.configuration
    }
}
