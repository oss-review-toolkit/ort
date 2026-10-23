/*
 * Copyright (C) 2017 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.utils.spdxexpression

import com.fasterxml.jackson.annotation.JsonCreator

import org.ossreviewtoolkit.utils.spdx.SpdxLicenseException

/**
 * An SPDX expression that contains a [license] with an [exception].
 */
class SpdxLicenseWithExceptionExpression(
    val license: SpdxSimpleExpression,
    val exception: String
) : SpdxSingleLicenseExpression() {
    companion object {
        /**
         * Parse a string into an [SpdxLicenseWithExceptionExpression]. Throws an [SpdxException] if the string cannot
         * be parsed. Throws a [ClassCastException] if the string is not an [SpdxLicenseWithExceptionExpression].
         */
        @JsonCreator
        @JvmStatic
        fun parse(expression: String): SpdxLicenseWithExceptionExpression =
            SpdxExpression.parse(expression) as SpdxLicenseWithExceptionExpression
    }

    override fun decompose() = setOf(this)

    override fun simpleLicense() = license.toString()

    override fun exception() = exception

    override fun normalize() =
        // Manually cast to SpdxSingleLicenseExpression as the type resolver does not recognize that in all subclasses
        // of SpdxSimpleExpression normalize() returns an SpdxSingleLicenseExpression.
        when (val normalizedLicense = license.normalize() as SpdxSingleLicenseExpression) {
            is SpdxSimpleExpression -> SpdxLicenseWithExceptionExpression(normalizedLicense, exception)

            // This case happens if a deprecated license identifier that contains an exception is used together with
            // another exception, for example "GPL-2.0-with-classpath-exception WITH Classpath-exception-2.0". If the
            // exceptions are equal ignore this issue, otherwise throw an exception.
            is SpdxLicenseWithExceptionExpression -> {
                if (normalizedLicense.exception == exception) {
                    normalizedLicense
                } else {
                    throw SpdxException(
                        "'$this' cannot be normalized, because the license '$license' contains the exception " +
                            "'${normalizedLicense.exception}' which is different from '$exception'."
                    )
                }
            }
        }

    override fun validate(strictness: Strictness) {
        license.validate(strictness)

        // Return early without any further checks on the exception in lenient mode.
        if (strictness == Strictness.ALLOW_ANY) return

        val spdxLicenseException = SpdxLicenseException.forId(exception)
        if (strictness == Strictness.ALLOW_DEPRECATED && spdxLicenseException != null) return

        val isCurrentLicenseException = spdxLicenseException?.deprecated == false
        if (strictness == Strictness.ALLOW_CURRENT && isCurrentLicenseException) return

        if (strictness == Strictness.ALLOW_LICENSEREF_EXCEPTIONS) {
            if (isCurrentLicenseException || SpdxLicenseReferenceExpression(exception).isException()) return
        }

        throw SpdxException("'$exception' is not a valid SPDX license exception string.")
    }

    override fun equals(other: Any?) =
        when (other) {
            is SpdxLicenseWithExceptionExpression -> {
                license == other.license && exception.equals(other.exception, ignoreCase = true)
            }

            else -> false
        }

    override fun hashCode() = license.hashCode() + 31 * exception.hashCode()

    override fun toString(): String = "$license $WITH $exception"

    override fun getLicenseUrl() = license.getLicenseUrl()
}
