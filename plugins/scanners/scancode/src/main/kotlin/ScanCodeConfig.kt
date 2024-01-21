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

package org.ossreviewtoolkit.plugins.scanners.scancode

import kotlin.math.max
import kotlin.time.Duration.Companion.minutes

import org.ossreviewtoolkit.utils.common.Options
import org.ossreviewtoolkit.utils.common.splitOnWhitespace

data class ScanCodeConfig(
    val commandLine: List<String>,
    val commandLineNonConfig: List<String>,
    val preferFileLicense: Boolean
) {
    companion object {
        /**
         * The default time after which scanning a file is aborted.
         */
        private val DEFAULT_TIMEOUT = 5.minutes

        /**
         * The default list of command line options that might have an impact on the scan results.
         */
        private val DEFAULT_COMMAND_LINE_OPTIONS = listOf(
            "--copyright",
            "--license",
            "--info",
            "--strip-root",
            "--timeout", "${DEFAULT_TIMEOUT.inWholeSeconds}"
        )

        /**
         * The default list of command line options that cannot have an impact on the scan results.
         */
        private val DEFAULT_COMMAND_LINE_NON_CONFIG_OPTIONS = listOf(
            "--processes", max(1, Runtime.getRuntime().availableProcessors() - 1).toString()
        )

        val DEFAULT = create(emptyMap())

        fun create(options: Options) =
            ScanCodeConfig(
                options["commandLine"]?.splitOnWhitespace() ?: DEFAULT_COMMAND_LINE_OPTIONS,
                options["commandLineNonConfig"]?.splitOnWhitespace()
                    ?: DEFAULT_COMMAND_LINE_NON_CONFIG_OPTIONS,
                options["preferFileLicense"].toBoolean()
            )
    }
}
