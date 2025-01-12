/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.scanners.fossid

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import org.apache.logging.log4j.kotlin.logger

/**
 * This class provides names for projects and scans when the FossID scanner creates them, following a given pattern.
 * If one or both patterns is null, a default naming convention is used.
 *
 * [namingScanPattern] and [namingProjectPattern] are patterns describing the name using variables, e.g. "$var1_$var2".
 * Variable values are given in the map [namingConventionVariables].
 *
 * There are also built-in variables. Built-in variables are prefixed in the pattern with "#" e.g. "$var1_#builtin".
 * Available built-in variables:
 * * **repositoryName**: The name of the repository (i.e., the part of the URL before .git).
 * * **currentTimestamp**: The current time.
 * * **deltaTag** (scan code only): If delta scans are enabled, this qualifies the scan as an *origin* scan or a *delta*
 * scan.
 * * **branch**: The branch name (revision) to scan. FossID only allows alphanumeric characters and '-' in names, all
 *   other characters are replaced with underscores. Might be shortened to fit the scan code length limit.
 */
class FossIdNamingProvider(
    private val namingProjectPattern: String?,
    private val namingScanPattern: String?,
    private val namingConventionVariables: Map<String, String>
) {
    companion object {
        @JvmStatic
        val FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")

        const val MAX_SCAN_CODE_LEN = 254
    }

    fun createProjectCode(repositoryName: String): String =
        namingProjectPattern?.let {
            val builtins = mapOf(
                "#repositoryName" to repositoryName
            )
            replaceNamingConventionVariables(namingProjectPattern, builtins, namingConventionVariables)
        } ?: repositoryName

    fun createScanCode(repositoryName: String, deltaTag: FossId.DeltaTag? = null, branch: String = ""): String {
        val pattern = namingScanPattern ?: buildString {
            append("#repositoryName_#currentTimestamp")
            if (deltaTag != null) append("_#deltaTag")
            if (branch.isNotBlank()) append("_#branch")
        }

        val builtins = mutableMapOf(
            "#repositoryName" to repositoryName,
            "#deltaTag" to deltaTag?.name?.lowercase().orEmpty()
        )

        builtins += "#branch" to normalizeBranchName(branch, pattern, builtins)

        return replaceNamingConventionVariables(pattern, builtins, namingConventionVariables)
    }

    /**
     * Replace characters in [branch] not matching `[a-zA-Z0-9-_]` with underscores and trim its length so that the
     * total length of the generated scan code does not exceed [MAX_SCAN_CODE_LEN].
     */
    private fun normalizeBranchName(
        branch: String,
        scanCodeNamingPattern: String,
        scanCodeVariables: Map<String, String>
    ): String {
        val noBranchScanCode =
            replaceNamingConventionVariables(
                scanCodeNamingPattern.replace("#branch", ""),
                scanCodeVariables,
                namingConventionVariables
            )

        require(noBranchScanCode.length < MAX_SCAN_CODE_LEN) {
            throw IllegalArgumentException(
                "FossID scan code '$noBranchScanCode' exceeds the limit of $MAX_SCAN_CODE_LEN characters. " +
                    "Please consider a shorter naming scan pattern."
            )
        }

        val maxBranchNameLength = MAX_SCAN_CODE_LEN - noBranchScanCode.length
        return branch.replace(Regex("[^a-zA-Z0-9-_]"), "_").take(maxBranchNameLength)
    }

    /**
     * Replace the naming convention variables with their values. Used for projects and scans.
     */
    private fun replaceNamingConventionVariables(
        namingConventionPattern: String,
        builtins: Map<String, String>,
        namingConventionVariables: Map<String, String>
    ): String {
        logger.info { "Parameterizing the name with pattern '$namingConventionPattern'." }
        val currentTimestamp = FORMATTER.format(LocalDateTime.now())

        val allVariables =
            namingConventionVariables.mapKeys { "\$${it.key}" } + builtins + ("#currentTimestamp" to currentTimestamp)

        return allVariables.entries.fold(namingConventionPattern) { acc, entry ->
            acc.replace(entry.key, entry.value)
        }
    }
}
