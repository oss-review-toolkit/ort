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

package org.ossreviewtoolkit.utils

import java.io.File

/**
 * Operating-System-specific utility functions.
 */
object Os {
    /**
     * The operating system name.
     */
    val name = System.getProperty("os.name").orEmpty()

    /**
     * The operating system name in lower case, for private use.
     */
    private val nameLowerCase = name.toLowerCase()

    /**
     * Whether the operating system is Linux or not.
     */
    val isLinux = "linux" in nameLowerCase

    /**
     * Whether the operating system is macOS or not.
     */
    val isMac = "mac" in nameLowerCase

    /**
     * Whether the operating system is Windows or not.
     */
    val isWindows = "windows" in nameLowerCase

    /**
     * The currently set environment variables. Keys are case-insensitive on Windows.
     */
    val env = System.getenv().let { env ->
        if (isWindows) env.toSortedMap(String.CASE_INSENSITIVE_ORDER) else env.toSortedMap()
    }

    /**
     * The current user's home directory.
     */
    val userHomeDirectory by lazy {
        File(fixupUserHomeProperty())
    }
}
