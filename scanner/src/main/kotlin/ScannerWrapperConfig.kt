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
    val matcherConfig: ScannerMatcherConfig,

    /**
     * If `true`, scan results of this scanner shall be read from the configured scan storages. If `null`, the default
     * value of the [ScannerWrapper] implementation will be used.
     */
    val readFromStorage: Boolean?,

    /**
     * If `true`, scan results for this scanner shall be written to the configured scan storages. If `null`, the default
     * value of the [ScannerWrapper] implementation will be used.
     */
    val writeToStorage: Boolean?
) {
    companion object {
        val EMPTY = ScannerWrapperConfig(
            matcherConfig = ScannerMatcherConfig.EMPTY,
            readFromStorage = null,
            writeToStorage = null
        )

        /**
         * The name of the boolean property to configure if scan results shall be read from the configured scan
         * storages.
         */
        internal const val PROP_READ_FROM_STORAGE = "readFromStorage"

        /**
         * The name of the boolean property to configure if scan results shall be written to the configured scan
         * storages.
         */
        internal const val PROP_WRITE_TO_STORAGE = "writeToStorage"

        private val properties = listOf(PROP_READ_FROM_STORAGE, PROP_WRITE_TO_STORAGE)

        /**
         * Create a [ScannerWrapperConfig] from the provided [options]. Return the created config and the options
         * without the properties that were used to create the [ScannerWrapperConfig].
         */
        fun create(options: Options): Pair<ScannerWrapperConfig, Options> {
            val (matcherConfig, filteredOptionsFromMatcher) = ScannerMatcherConfig.create(options)
            val filteredOptions = filteredOptionsFromMatcher.filterKeys { it !in properties }

            return ScannerWrapperConfig(
                matcherConfig = matcherConfig,
                readFromStorage = options[PROP_READ_FROM_STORAGE]?.toBooleanStrict(),
                writeToStorage = options[PROP_WRITE_TO_STORAGE]?.toBooleanStrict()
            ) to filteredOptions
        }
    }

    /**
     * Return [readFromStorage] if it is not `null`, otherwise return `true` if the provided [matcher] is not `null`.
     */
    fun readFromStorageWithDefault(matcher: ScannerMatcher?) = readFromStorage ?: (matcher != null)

    /**
     * Return [writeToStorage] if it is not `null`, otherwise return `true` if the provided [matcher] is not `null`.
     */
    fun writeToStorageWithDefault(matcher: ScannerMatcher?) = writeToStorage ?: (matcher != null)
}
