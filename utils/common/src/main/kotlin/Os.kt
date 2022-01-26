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

package org.ossreviewtoolkit.utils.common

import java.io.File
import java.lang.IllegalArgumentException

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
    private val nameLowerCase = name.lowercase()

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
    val env by lazy {
        System.getenv().let { env ->
            if (isWindows) env.toSortedMap(String.CASE_INSENSITIVE_ORDER) else env.toSortedMap()
        }
    }

    /**
     * The current user's home directory.
     */
    val userHomeDirectory by lazy {
        File(fixupUserHomeProperty())
    }

    /**
     * Check if the "user.home" property is set to a sane value and otherwise set it to the value of an (OS-specific)
     * environment variable for the user home directory, and return that value. This works around the issue that esp. in
     * certain Docker scenarios "user.home" is set to "?", see https://bugs.openjdk.java.net/browse/JDK-8193433 for some
     * background information.
     */
    fun fixupUserHomeProperty(): String {
        val userHome = System.getProperty("user.home")
        if (!userHome.isNullOrBlank() && userHome != "?") return userHome

        val fallbackUserHome = listOfNotNull(
            env["HOME"],
            env["USERPROFILE"]
        ).find {
            it.isNotBlank()
        } ?: throw IllegalArgumentException("Unable to determine a user home directory.")

        System.setProperty("user.home", fallbackUserHome)

        return fallbackUserHome
    }

    /**
     * Return the full path to the given executable file if it is in the system's PATH environment, or null otherwise.
     */
    fun getPathFromEnvironment(executable: String): File? {
        fun String.expandVariable(referencePattern: Regex, groupName: String): String =
            replace(referencePattern) {
                val variableName = it.groups[groupName]!!.value
                env[variableName] ?: variableName
            }

        val paths = env["PATH"]?.splitToSequence(File.pathSeparatorChar).orEmpty()

        return if (isWindows) {
            val referencePattern = Regex("%(?<reference>\\w+)%")

            paths.firstNotNullOfOrNull { path ->
                val expandedPath = path.expandVariable(referencePattern, "reference")
                resolveWindowsExecutable(File(expandedPath, executable))
            }
        } else {
            val referencePattern = Regex("\\$\\{?(?<reference>\\w+)}?")

            paths.map { path ->
                val expandedPath = path.expandVariable(referencePattern, "reference")
                File(expandedPath, executable)
            }.find { it.isFile }
        }
    }

    /**
     * Resolve the Windows [executable] to its full name including the optional extension.
     */
    fun resolveWindowsExecutable(executable: File): File? {
        val extensions = env["PATHEXT"]?.splitToSequence(File.pathSeparatorChar).orEmpty()
        return extensions.map { File(executable.path + it.lowercase()) }.find { it.isFile }
            ?: executable.takeIf { it.isFile }
    }
}
