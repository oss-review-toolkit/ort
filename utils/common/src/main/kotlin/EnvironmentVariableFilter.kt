/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import org.apache.logging.log4j.kotlin.Logging

/**
 * An object providing functionality to filter environments that are passed to newly created processes.
 *
 * For many tasks, ORT spawns new processes using the [ProcessCapture] class. When creating a new process, the child
 * process by default inherits all environment variables from the parent. This could impose a security risk, for
 * instance if logic in build scripts could theoretically access sensitive information stored in environment
 * variables, such as database or service credentials.
 *
 * To reduce this risk, this object filters the environment variables passed to child processes based on the
 * following criteria:
 * - Substrings for variable names can be defined to determine variables with sensitive information. The object provides
 *   some default strings to match variable names like "PASS", "USER", "TOKEN", etc.
 * - There is an allow list to include variables even if they contain one of these substrings.
 *
 * So in order to determine whether a specific variable "E" can be passed to a child process, this filter applies the
 * following steps:
 * - If E is contained in the allow list, it is included.
 * - Otherwise, E is included if and only if its name does not contain one of the exclusion substrings (ignoring case).
 *
 * TODO: Find an alternative mechanism to initialize this object from the ORT configuration (maybe using dependency
 *       injection) which does not require this object to be public.
 */
object EnvironmentVariableFilter : Logging {
    /**
     * A set with substrings contained in variable names that are denied by default. All variables containing one of
     * these strings (ignoring case) are not propagated to child processes.
     */
    val DEFAULT_DENY_SUBSTRINGS = setOf(
        "key",
        "pass",
        "pwd",
        "token",
        "user"
    )

    /** A set of known variable names that are allowed despite being matched by deny substrings. */
    val DEFAULT_ALLOW_NAMES = setOf(
        "CARGO_HTTP_USER_AGENT",
        "COMPOSER_ALLOW_SUPERUSER",
        "CONAN_LOGIN_ENCRYPTION_KEY",
        "CONAN_LOGIN_USERNAME",
        "CONAN_PASSWORD",
        "CONAN_USERNAME",
        "CONAN_USER_HOME",
        "CONAN_USER_HOME_SHORT",
        "DOTNET_CLI_CONTEXT_ANSI_PASS_THRU",
        "GIT_ASKPASS",
        "GIT_HTTP_USER_AGENT",
        "GRADLE_USER_HOME",
        "HACKAGE_USERNAME",
        "HACKAGE_PASSWORD",
        "HACKAGE_KEY",
        "PWD",
        "USER",
        "USERPROFILE"
    )

    /** Stores the current deny substrings used by this filter. */
    private var denySubstrings = DEFAULT_DENY_SUBSTRINGS

    /** Stores the current allow names used by this filter. */
    private var allowNames = DEFAULT_ALLOW_NAMES

    /**
     * Reset this filter to use the given [denySubstrings] and [allowNames].
     */
    fun reset(
        denySubstrings: Collection<String> = DEFAULT_DENY_SUBSTRINGS,
        allowNames: Collection<String> = DEFAULT_ALLOW_NAMES
    ) {
        this.denySubstrings = denySubstrings.toSet()
        this.allowNames = allowNames.toSet()

        logger.info {
            "EnvironmentVariableFilter initialized with denySubstrings = $denySubstrings and allowNames = $allowNames."
        }
    }

    /**
     * Test whether the variable with the given [name] can be passed to a child process according to the criteria
     * described in the header comment.
     */
    fun isAllowed(name: String): Boolean {
        return name in allowNames || denySubstrings.none { name.contains(it, ignoreCase = true) }
    }

    /**
     * Remove all keys from [environment] that do not pass this filter.
     */
    fun filter(environment: MutableMap<String, String>): MutableMap<String, String> {
        val keysToRemove = environment.keys.filterNot(EnvironmentVariableFilter::isAllowed)

        if (keysToRemove.isNotEmpty()) {
            logger.debug { "Filtering out these variables from the environment: $keysToRemove" }
        }

        @Suppress("ConvertArgumentToSet") // The list cannot contain duplicates.
        environment -= keysToRemove

        return environment
    }
}
