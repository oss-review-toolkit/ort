/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package com.here.ort.evaluator

import com.here.ort.model.Package

/**
 * A [LicenseView] provides a custom view on the licenses that belong to a [Package]. It can be used to filter the
 * relevant licenses by [source][LicenseSource] for a [Rule].
 */
sealed class LicenseView {
    abstract fun licenses(pkg: Package, detectedLicenses: List<String>): List<Pair<String, LicenseSource>>

    /**
     * Return all licenses.
     */
    object All : LicenseView() {
        override fun licenses(pkg: Package, detectedLicenses: List<String>): List<Pair<String, LicenseSource>> {
            val concluded = pkg.concludedLicense?.licenses()?.map { license ->
                Pair(license, LicenseSource.CONCLUDED)
            }.orEmpty()

            val declared =
                pkg.declaredLicensesProcessed.spdxExpression?.licenses()?.map { Pair(it, LicenseSource.DECLARED) }
                    .orEmpty()

            val detected = detectedLicenses.map { Pair(it, LicenseSource.DETECTED) }

            return concluded + declared + detected
        }
    }

    /**
     * Return only the concluded licenses if they exist, otherwise return declared and detected licenses.
     */
    object ConcludedOrRest : LicenseView() {
        override fun licenses(pkg: Package, detectedLicenses: List<String>): List<Pair<String, LicenseSource>> {
            pkg.concludedLicense?.licenses()?.let {
                return it.map { license -> Pair(license, LicenseSource.CONCLUDED) }
            }

            val declared =
                pkg.declaredLicensesProcessed.spdxExpression?.licenses()?.map { Pair(it, LicenseSource.DECLARED) }
                    .orEmpty()

            val detected = detectedLicenses.map { Pair(it, LicenseSource.DETECTED) }

            return declared + detected
        }
    }

    /**
     * Return only the concluded licenses if they exist, or return only the declared licenses if they exist, or return
     * the detected licenses.
     */
    object ConcludedOrDeclaredOrDetected : LicenseView() {
        override fun licenses(pkg: Package, detectedLicenses: List<String>): List<Pair<String, LicenseSource>> {
            pkg.concludedLicense?.licenses()?.let {
                return it.map { license -> Pair(license, LicenseSource.CONCLUDED) }
            }

            pkg.declaredLicensesProcessed.spdxExpression?.licenses()?.let {
                return it.map { license -> Pair(license, LicenseSource.DECLARED) }
            }

            return detectedLicenses.map { Pair(it, LicenseSource.DETECTED) }
        }
    }

    object OnlyDetected : LicenseView() {
        override fun licenses(pkg: Package, detectedLicenses: List<String>): List<Pair<String, LicenseSource>> {
            return detectedLicenses.map { Pair(it, LicenseSource.DETECTED) }
        }
    }
}
