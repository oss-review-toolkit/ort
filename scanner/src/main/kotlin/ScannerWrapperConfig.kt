/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import org.ossreviewtoolkit.utils.common.Options

/**
 * A class to hold the configuration that is common to all [ScannerWrapper]s.
 */
data class ScannerWrapperConfig(
    /**
     * The configuration for the [ScannerMatcher].
     */
    val matcherConfig: ScannerMatcherConfig
) {
    companion object {
        /**
         * Create a [ScannerWrapperConfig] from the provided [options]. Return the created config and the options
         * without the properties that were used to create the [ScannerWrapperConfig].
         */
        fun create(options: Options): Pair<ScannerWrapperConfig, Options> {
            val (matcherConfig, filteredOptions) = ScannerMatcherConfig.create(options)
            return ScannerWrapperConfig(matcherConfig) to filteredOptions
        }
    }
}
