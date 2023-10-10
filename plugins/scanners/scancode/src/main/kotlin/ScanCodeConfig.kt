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

import org.ossreviewtoolkit.utils.common.Options

data class ScanCodeConfig(
    val commandLine: String?,
    val commandLineNonConfig: String?
) {
    companion object {
        val EMPTY = ScanCodeConfig(null, null)

        private const val COMMAND_LINE_PROPERTY = "commandLine"
        private const val COMMAND_LINE_NON_CONFIG_PROPERTY = "commandLineNonConfig"

        fun create(options: Options) =
            ScanCodeConfig(options[COMMAND_LINE_PROPERTY], options[COMMAND_LINE_NON_CONFIG_PROPERTY])
    }
}
