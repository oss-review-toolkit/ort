/*
 * Copyright (C) 2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.utils.spdx.model

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * A unique identifier based on the file contents of a package.
 */
data class SpdxPackageVerificationCode(
    /**
     * The list of file excluded from the package verification code calculation.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val packageVerificationCodeExcludedFiles: List<String> = emptyList(),

    /**
     * The package verification code value in lower case hexadecimal representation.
     */
    val packageVerificationCodeValue: String
) {
    init {
        require(packageVerificationCodeValue.matches(SpdxChecksum.HEX_SYMBOLS_REGEX)) {
            "The package verification code must only contain lower case hexadecimal digits but was " +
                    "'$packageVerificationCodeValue'."
        }

        require(SpdxChecksum.Algorithm.SHA1.checksumHexDigits == packageVerificationCodeValue.length) {
            "Expected a checksum value with ${SpdxChecksum.Algorithm.SHA1.checksumHexDigits} hexadecimal digits, but " +
                    "found ${packageVerificationCodeValue.length}."
        }
    }
}
