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
 * There also are builtin variables. Builtin variables are prefixed in the pattern with "#" e.g. "$var1_#builtin".
 * Available builtin variables:
 * * **projectName**: The name of the project (i.e. the part of the URL before .git).
 * * **currentTimestamp**: The current time.
 * * **deltaTag** (scan code only): If delta scans is enabled, this qualifies the scan as an *origin* scan or a *delta*
 * scan.
 * * **branch**: branch name (revision) given to scan
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

    fun createProjectCode(projectName: String): String =
        namingProjectPattern?.let {
            val builtins = mapOf(
                "#projectName" to projectName
            )
            replaceNamingConventionVariables(namingProjectPattern, builtins, namingConventionVariables)
        } ?: projectName

    fun createScanCode(projectName: String, deltaTag: FossId.DeltaTag? = null, branch: String = ""): String {
        return namingScanPattern?.let {
            createScanCodeForCustomPattern(namingScanPattern, projectName, deltaTag, branch)
        } ?: run {
            createScanCodeForDefaultPattern(projectName, deltaTag, branch)
        }
    }

    private fun createScanCodeForDefaultPattern(
        projectName: String,
        deltaTag: FossId.DeltaTag? = null,
        branch: String = ""
    ): String {
        val builtins = mutableMapOf("#projectName" to projectName)
        var defaultPattern = "#projectName_#currentTimestamp"

        deltaTag?.let {
            defaultPattern += "_#deltaTag"
            builtins += "#deltaTag" to deltaTag.name.lowercase()
        }

        if (branch.isNotBlank()) {
            val branchName = normalizeBranchName(branch, defaultPattern, builtins)
            defaultPattern += "_#branch"
            builtins += "#branch" to branchName
        }

        return replaceNamingConventionVariables(defaultPattern, builtins, namingConventionVariables)
    }

    private fun createScanCodeForCustomPattern(
        namingPattern: String,
        projectName: String,
        deltaTag: FossId.DeltaTag? = null,
        branch: String = ""
    ): String {
        val builtins = mutableMapOf<String, String>()

        namingPattern.contains("#projectName").let {
            builtins += "#projectName" to projectName
        }

        namingPattern.contains("#deltaTag").let {
            if (deltaTag != null) {
                builtins += "#deltaTag" to deltaTag.name.lowercase()
            }
        }

        namingPattern.contains("#branch").let {
            val namingPatternWithoutBranchPlaceholder = namingPattern.replace("#branch", "")
            builtins += "#branch" to normalizeBranchName(branch, namingPatternWithoutBranchPlaceholder, builtins)
        }

        return replaceNamingConventionVariables(namingPattern, builtins, namingConventionVariables)
    }

    /**
     * Replaces non-standard characters in branch name and trims its length to one that will not exceed
     * maximum length of FossID scan ID, when combined with the rest of variables
     */
    private fun normalizeBranchName(
        branch: String,
        scanCodeNamingPattern: String,
        scanCodeVariables: Map<String, String>
    ): String {
        val noBranchScanCode =
            replaceNamingConventionVariables(
                scanCodeNamingPattern,
                scanCodeVariables,
                namingConventionVariables
            )

        require(noBranchScanCode.length < MAX_SCAN_CODE_LEN) {
            throw IllegalArgumentException(
                "FossID scan code '$noBranchScanCode' is too long. " +
                    "It must not exceed $MAX_SCAN_CODE_LEN characters. Please consider shorter naming scan pattern."
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
