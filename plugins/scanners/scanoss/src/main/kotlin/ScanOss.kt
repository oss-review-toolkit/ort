/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.scanners.scanoss

import com.scanoss.Scanner
import com.scanoss.filters.FilterConfig
import com.scanoss.settings.Bom
import com.scanoss.settings.RemoveRule
import com.scanoss.settings.ReplaceRule
import com.scanoss.settings.Rule
import com.scanoss.settings.ScanossSettings
import com.scanoss.utils.JsonUtils
import com.scanoss.utils.PackageDetails

import java.io.File
import java.time.Instant

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.config.SnippetChoices
import org.ossreviewtoolkit.model.config.snippet.SnippetChoice
import org.ossreviewtoolkit.model.config.snippet.SnippetChoiceReason
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.scanner.PathScannerWrapper
import org.ossreviewtoolkit.scanner.ScanContext
import org.ossreviewtoolkit.scanner.ScannerMatcher
import org.ossreviewtoolkit.scanner.ScannerWrapperFactory

@OrtPlugin(
    id = "SCANOSS",
    displayName = "SCANOSS",
    description = "A wrapper for the SCANOSS snippet scanner.",
    factory = ScannerWrapperFactory::class
)
class ScanOss(
    override val descriptor: PluginDescriptor = ScanOssFactory.descriptor,
    config: ScanOssConfig
) : PathScannerWrapper {
    private val scanossBuilder = Scanner.builder()
        // As there is only a single endpoint, the SCANOSS API client expects the path to be part of the API URL.
        .url(config.apiUrl.removeSuffix("/") + "/scan/direct")
        .apiKey(config.apiKey.value)
        .obfuscate(config.enablePathObfuscation)

    override val version: String by lazy {
        // TODO: Find out the best / cheapest way to query the SCANOSS server for its version.
        PackageDetails.getVersion()
    }

    override val configuration = ""

    override val matcher: ScannerMatcher? = null

    override val readFromStorage = false

    override val writeToStorage = config.writeToStorage

    override fun scanPath(path: File, context: ScanContext): ScanSummary {
        val startTime = Instant.now()

        val filterConfig = FilterConfig.builder()
            .customFilter { currentPath ->
                // The "currentPath" variable contains a path object representing the file or directory being evaluated
                // by the filter.
                // This is provided by the Scanner and represents individual files/directories during traversal.
                try {
                    val relativePath = currentPath.toFile().toRelativeString(path)
                    val isExcluded = context.excludes?.isPathExcluded(relativePath) ?: false
                    logger.debug { "Path: $currentPath, relative: $relativePath, isExcluded: $isExcluded" }
                    isExcluded
                } catch (e: IllegalArgumentException) {
                    logger.warn { "Error processing path $currentPath: ${e.message}" }
                    false
                }
            }
            .build()

        // Build the scanner at function level in case any path-specific settings or filters are needed later.
        val scanoss = scanossBuilder
            .settings(buildSettingsFromORTContext(context))
            .filterConfig(filterConfig)
            .build()

        val rawResults = when {
            path.isFile -> listOf(scanoss.scanFile(path.toString()))
            else -> scanoss.scanFolder(path.toString())
        }

        val results = JsonUtils.toScanFileResults(rawResults)
        val endTime = Instant.now()
        return generateSummary(startTime, endTime, results)
    }

    data class ProcessedRules(
        val includeRules: List<Rule>,
        val ignoreRules: List<Rule>,
        val replaceRules: List<ReplaceRule>,
        val removeRules: List<RemoveRule>
    )

    private fun buildSettingsFromORTContext(context: ScanContext): ScanossSettings {
        val rules = processSnippetChoices(context.snippetChoices)
        val bom = Bom.builder()
            .ignore(rules.ignoreRules)
            .include(rules.includeRules)
            .replace(rules.replaceRules)
            .remove(rules.removeRules)
            .build()
        return ScanossSettings.builder().bom(bom).build()
    }

    fun processSnippetChoices(snippetChoices: List<SnippetChoices>): ProcessedRules {
        val includeRules = mutableListOf<Rule>()
        val ignoreRules = mutableListOf<Rule>()
        val replaceRules = mutableListOf<ReplaceRule>()
        val removeRules = mutableListOf<RemoveRule>()

        snippetChoices.forEach { snippetChoice ->
            snippetChoice.choices.forEach { choice ->
                when (choice.choice.reason) {
                    SnippetChoiceReason.ORIGINAL_FINDING -> {
                        includeRules.includeFinding(choice)
                    }

                    SnippetChoiceReason.NO_RELEVANT_FINDING -> {
                        removeRules.removeFinding(choice)
                    }

                    SnippetChoiceReason.OTHER -> {
                        logger.info {
                            "Encountered OTHER reason for snippet choice in file ${choice.given.sourceLocation.path}"
                        }
                    }
                }
            }
        }

        return ProcessedRules(includeRules, ignoreRules, replaceRules, removeRules)
    }

    private fun MutableList<Rule>.includeFinding(choice: SnippetChoice) {
        this += Rule.builder()
            .purl(choice.choice.purl)
            .path(choice.given.sourceLocation.path)
            .build()
    }

    private fun MutableList<RemoveRule>.removeFinding(choice: SnippetChoice) {
        this += RemoveRule.builder().apply {
            path(choice.given.sourceLocation.path)

            // Set line range only if both line positions (startLine and endLine) are known.
            if (choice.given.sourceLocation.hasLineRange) {
                startLine(choice.given.sourceLocation.startLine)
                endLine(choice.given.sourceLocation.endLine)
            }
        }.build()
    }
}
