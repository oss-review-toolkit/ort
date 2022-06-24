/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.scanner.scanners.fossid

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import org.ossreviewtoolkit.utils.ort.log

/**
 * This class provides names for projects and scans when the FossID scanner creates them, following a given pattern.
 * If one or both patterns is null, a default naming convention is used.
 *
 * [namingScanPattern] and [namingProjectPattern] are patterns describing the name using variables, e.g. "$var1_$var2".
 * Variable values are given in the map [namingConventionVariables].
 *
 * There also are builtin variables. Builtin variables are prefixed in the pattern with "#" e.g. "$var1_#builtin".
 * Available builtin variables:
 * * **projectName**: The name of the project (i.e. the part of the URL before .git).
 * * **currentTimestamp**: The current time.
 * * **deltaTag** (scan code only): If delta scans is enabled, this qualifies the scan as an *origin* scan or a *delta*
 * scan.
 */
internal class FossIdNamingProvider(
    private val namingProjectPattern: String?,
    private val namingScanPattern: String?,
    private val namingConventionVariables: Map<String, String>
) {
    companion object {
        @JvmStatic
        val FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    }

    fun createProjectCode(projectName: String): String = namingProjectPattern?.let {
        val builtins = mapOf(
            "#projectName" to projectName
        )
        replaceNamingConventionVariables(namingProjectPattern, builtins, namingConventionVariables)
    } ?: projectName

    fun createScanCode(projectName: String, deltaTag: FossId.DeltaTag? = null): String {
        var defaultPattern = "#projectName_#currentTimestamp"
        val builtins = mutableMapOf("#projectName" to projectName)

        deltaTag?.let {
            defaultPattern += "_#deltaTag"
            builtins += "#deltaTag" to deltaTag.name.lowercase()
        }

        val pattern = namingScanPattern ?: defaultPattern
        return replaceNamingConventionVariables(pattern, builtins, namingConventionVariables)
    }

    /**
     * Replace the naming convention variables with their values. Used for projects and scans.
     */
    private fun replaceNamingConventionVariables(
        namingConventionPattern: String, builtins: Map<String, String>, namingConventionVariables: Map<String, String>
    ): String {
        log.info { "Parameterizing the name with pattern '$namingConventionPattern'." }
        val currentTimestamp = FORMATTER.format(LocalDateTime.now())

        val allVariables =
            namingConventionVariables.mapKeys { "\$${it.key}" } + builtins + ("#currentTimestamp" to currentTimestamp)

        return allVariables.entries.fold(namingConventionPattern) { acc, entry ->
            acc.replace(entry.key, entry.value)
        }
    }
}
