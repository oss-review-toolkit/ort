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

import org.apache.logging.log4j.Level

/**
 * A generic class describing a severity, e.g. of issues, sorted from least severe to most severe.
 */
enum class Severity {
    /**
     * A hint is something that is provided for information only.
     */
    HINT,

    /**
     * A warning is something that should be addressed.
     */
    WARNING,

    /**
     * An error is something that has to be addressed.
     */
    ERROR;

    /**
     * Map the [Severity] to a Log4j [Level].
     */
    fun toLog4jLevel(): Level =
        when (this) {
            HINT -> Level.INFO
            WARNING -> Level.WARN
            ERROR -> Level.ERROR
        }
}
