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

/**
 * An SPDX expression that contains only a single license with an optional exception. Can be either a
 * [SpdxLicenseWithExceptionExpression] or any subtype of [SpdxSimpleExpression].
 */
sealed class SpdxSingleLicenseExpression : SpdxExpression() {
    companion object {
        /**
         * Parse a string into an [SpdxSingleLicenseExpression]. Throws an [SpdxException] if the string cannot be
         * parsed. Throws a [ClassCastException] if the string is an [SpdxCompoundExpression].
         */
        @JsonCreator
        @JvmStatic
        fun parse(expression: String): SpdxSingleLicenseExpression =
            SpdxExpression.parse(expression) as SpdxSingleLicenseExpression
    }

    /**
     * Return the string identifier of this license without any license exception.
     */
    abstract fun simpleLicense(): String

    /**
     * Return the license exception identifier if this is an [SpdxLicenseWithExceptionExpression] or null otherwise.
     */
    abstract fun exception(): String?

    /**
     * Return the URL for the licence if this is an [SpdxLicenseIdExpression] or [SpdxLicenseWithExceptionExpression].
     * Otherwise, return null.
     */
    abstract fun getLicenseUrl(): String?
}
