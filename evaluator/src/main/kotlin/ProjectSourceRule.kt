/*
 * Copyright (C) 2022 EPAM Systems, Inc.
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

package org.ossreviewtoolkit.evaluator

import java.io.File

import org.ossreviewtoolkit.model.LicenseSource
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.utils.getRepositoryPath
import org.ossreviewtoolkit.utils.common.FileMatcher

/**
 * An [OrtResultRule] which allows downloading the project's source code if needed.
 */
open class ProjectSourceRule(
    ruleSet: RuleSet,
    name: String,
    projectSourceResolver: SourceTreeResolver = ruleSet.ortResult.createResolver()
) : OrtResultRule(ruleSet, name) {
    /**
     * The directory containing the source code of the project. Accessing the property for the first time triggers a
     * clone and may take a while.
     */
    @Suppress("MemberVisibilityCanBePrivate") // This property is used in rules.
    val projectSourcesDir: File by lazy { projectSourceResolver.rootDir }

    private val detectedLicensesForFilePath: Map<String, Set<String>> by lazy {
        val result = mutableMapOf<String, MutableSet<String>>()
        val projectIds = ruleSet.ortResult.getProjects().map { it.id }
        val licenseInfos = projectIds.map { ruleSet.licenseInfoResolver.resolveLicenseInfo(it) }

        licenseInfos.forEach { licenseInfo ->
            licenseInfo.licenses.filter {
                LicenseSource.DETECTED in it.sources
            }.forEach { resolvedLicense ->
                resolvedLicense.locations.forEach { resolvedLocation ->
                    val provenance = resolvedLocation.provenance as RepositoryProvenance
                    val repositoryPath = ortResult.getRepositoryPath(provenance)
                    val path = "$repositoryPath${resolvedLocation.location.path}".removePrefix("/")

                    result.getOrPut(path) { mutableSetOf() } += resolvedLicense.license.simpleLicense()
                }
            }
        }

        result
    }

    /**
     * Return all directories from the project's source tree which match any of the
     * provided [glob expressions][patterns].
     */
    fun projectSourceFindDirectories(vararg patterns: String): List<File> =
        projectSourcesDir.walkBottomUp().filterTo(mutableListOf()) {
            it.isDirectory && FileMatcher.match(patterns.toList(), it.relativeTo(projectSourcesDir).path)
        }

    /**
     * Return all files from the project's source tree which match any of the provided [glob expressions][patterns].
     */
    fun projectSourceFindFiles(vararg patterns: String): List<File> =
        projectSourcesDir.walkBottomUp().filterTo(mutableListOf()) {
            it.isFile && FileMatcher.match(patterns.toList(), it.relativeTo(projectSourcesDir).path)
        }

    /**
     * Return the detected licenses for any file matching the given [glob expressions][patterns].
     */
    fun projectSourceGetDetectedLicensesByFilePath(
        vararg patterns: String
    ): Map<String, Set<String>> =
        detectedLicensesForFilePath.filter { (filepath, _) ->
            FileMatcher.match(patterns.toList(), filepath)
        }

    /**
     * Return the file paths matching any of the given [glob expressions][patterns] with its file content matching
     * [contentPattern].
     */
    fun projectSourceFindFilesWithContent(contentPattern: String, vararg patterns: String): List<File> {
        val regex = contentPattern.toRegex(setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE))

        return projectSourceFindFiles(*patterns).filter { path ->
            val content = projectSourcesDir.resolve(path).readText()

            content.matches(regex)
        }
    }

    /**
     * A [RuleMatcher] that checks whether the project's source tree contains at least one directory matching any of the
     * provided [glob expressions][patterns].
     */
    fun projectSourceHasDirectory(vararg patterns: String): RuleMatcher =
        object : RuleMatcher {
            override val description = "projectSourceHasDirectory('${patterns.joinToString()}')"

            override fun matches(): Boolean = projectSourceFindDirectories(*patterns).isNotEmpty()
        }

    /**
     * A [RuleMatcher] that checks whether the project's source tree contains at least one file matching any of the
     * provided [glob expressions][patterns].
     */
    fun projectSourceHasFile(vararg patterns: String): RuleMatcher =
        object : RuleMatcher {
            override val description = "projectSourceHasFile('${patterns.joinToString()}')"
            override fun matches(): Boolean = projectSourceFindFiles(*patterns).isNotEmpty()
        }

    /**
     * A [RuleMatcher] that checks whether the project's source tree contains at least one file matching any of the
     * given [glob expressions][patterns] with its file content matching [contentPattern].
     */
    fun projectSourceHasFileWithContent(contentPattern: String, vararg patterns: String): RuleMatcher =
        object : RuleMatcher {
            override val description =
                "projectSourceHasFileWithContents('$contentPattern', '${patterns.joinToString()}')"

            override fun matches(): Boolean =
                projectSourceFindFilesWithContent(contentPattern, *patterns).isNotEmpty()
        }
}

private fun OrtResult.createResolver() =
    SourceTreeResolver.forRemoteRepository(repository.vcsProcessed)
