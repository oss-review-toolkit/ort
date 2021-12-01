/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.model

/**
 * Details about the used source code scanner.
 */
data class ScannerDetails(
    /**
     * The name of the scanner.
     */
    val name: String,

    /**
     * The version of the scanner.
     */
    val version: String,

    /**
     * The configuration of the scanner, could be command line arguments for example.
     */
    val configuration: String
) {
    companion object {
        /**
         * A constant for a [ScannerDetails] where all properties are empty strings.
         */
        @JvmField
        val EMPTY = ScannerDetails(
            name = "",
            version = "",
            configuration = ""
        )
    }
}
