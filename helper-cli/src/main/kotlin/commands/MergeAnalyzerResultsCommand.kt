/*
 * Copyright (C) 2022 Porsche AG
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

package org.ossreviewtoolkit.helper.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.options.split
import com.github.ajalt.clikt.parameters.types.file

import java.io.File
import java.time.Instant
import java.util.LinkedList
import java.util.SortedMap
import java.util.SortedSet
import java.util.TreeMap
import java.util.TreeSet

import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.AnalyzerRun
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Curations
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.LicenseChoices
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.Resolutions
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.writeValue
import org.ossreviewtoolkit.utils.common.expandTilde
import org.ossreviewtoolkit.utils.core.Environment

class MergeAnalyzerResultsCommand : CliktCommand(
    help = "Read multiple analyzer result files and merge them into one combined analyzer result file. " +
            "This tool reduces the number of projects in the output analyzer result to one and merges the " +
            "dependency graph per scope across all input analyzer result projects."
) {
    private val inputAnalyzerResultFiles by option(
        "--input-analyzer-result-files", "-i",
        help = "A comma separated list of analyzer result files to be merged."
    ).convert { File(it.expandTilde()).absoluteFile.normalize() }.split(",").required()

    private val outputAnalyzerResultFile by option(
        "--output-analyzer-result-file", "-o",
        help = "The output analyer file."
    ).convert { it.expandTilde() }
        .file(mustExist = false, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()

    override fun run() {
        val inputAnalyzerResults: MutableList<AnalyzerRun> = LinkedList()
        val inputRepositories: MutableList<Repository> = LinkedList()

        inputAnalyzerResultFiles
            .map { it.readValue<OrtResult>() }
            .forEach {
                it.analyzer?.let { analyzerRun ->
                    inputAnalyzerResults.add(analyzerRun)
                    inputRepositories.add(it.repository)
                }
            }

        val analyzerRun = AnalyzerRun(
            startTime = Instant.now(),
            endTime = Instant.now(),
            environment = Environment(),
            config = aggregateAnalyzerConfigurations(inputAnalyzerResults),
            result = aggregrateAnalyzerRuns(inputAnalyzerResults)
        )

        val repository = Repository(
            vcs = analyzerRun.result.projects.first().vcs,
            vcsProcessed = analyzerRun.result.projects.first().vcsProcessed,
            config = aggregateRepositoryConfigurations(inputRepositories)
        )

        val outputFile = OrtResult(
            repository = repository,
            analyzer = analyzerRun
        )

        outputAnalyzerResultFile.writeValue(outputFile)
    }

    private fun aggregateRepositoryConfigurations(repositories: List<Repository>): RepositoryConfiguration {
        fun mergeResolutions(leftResolutions: Resolutions, rightResolutions: Resolutions): Resolutions =
            Resolutions(
                issues = (leftResolutions.issues + rightResolutions.issues).distinct(),
                ruleViolations = (leftResolutions.ruleViolations + rightResolutions.ruleViolations).distinct(),
                vulnerabilities = (leftResolutions.vulnerabilities + rightResolutions.vulnerabilities).distinct()
            )

        fun mergeCurations(leftCurations: Curations, rightCurations: Curations): Curations =
            Curations(
                licenseFindings = (leftCurations.licenseFindings + rightCurations.licenseFindings).distinct()
            )

        fun mergeLicenseChoices(leftChoices: LicenseChoices, rightChoices: LicenseChoices): LicenseChoices =
            LicenseChoices(
                repositoryLicenseChoices = (
                        leftChoices.repositoryLicenseChoices
                                + rightChoices.repositoryLicenseChoices
                        ).distinct(),
                packageLicenseChoices = (
                        leftChoices.packageLicenseChoices
                                + rightChoices.packageLicenseChoices
                        ).distinct()
            )

        return repositories
            .map { it.config }
            .reduceOrNull { leftConfig, rightConfig ->
                RepositoryConfiguration(
                    excludes = Excludes(),
                    resolutions = mergeResolutions(leftConfig.resolutions, rightConfig.resolutions),
                    curations = mergeCurations(leftConfig.curations, rightConfig.curations),
                    licenseChoices = mergeLicenseChoices(leftConfig.licenseChoices, rightConfig.licenseChoices)
                )
            } ?: RepositoryConfiguration()
    }

    private fun aggregateAnalyzerConfigurations(inputOrtResults: List<AnalyzerRun>): AnalyzerConfiguration {
        return inputOrtResults
            .map { it.config }
            .reduceOrNull { a, b ->
                AnalyzerConfiguration(
                    sw360Configuration = if (a.sw360Configuration != null)
                        a.sw360Configuration
                    else
                        b.sw360Configuration,
                    allowDynamicVersions = a.allowDynamicVersions or b.allowDynamicVersions
                )
            } ?: AnalyzerConfiguration(allowDynamicVersions = false)
    }

    private fun aggregrateAnalyzerRuns(inputOrtResults: List<AnalyzerRun>): AnalyzerResult {
        return inputOrtResults
            .map { it.result }
            .reduceOrNull { leftResult, rightResult ->
                AnalyzerResult(
                    projects = wrapValueInSortedSet(
                        mergeProjects(
                            mergeSortedSets(
                                leftResult.projects,
                                rightResult.projects
                            )
                        )
                    ),
                    packages = mergeSortedSets(leftResult.packages, rightResult.packages),
                    issues = mergeIssues(leftResult.issues, rightResult.issues)
                )
            } ?: AnalyzerResult.EMPTY
    }

    private fun <T> mergeSortedSets(left: SortedSet<T>, right: SortedSet<T>): SortedSet<T> = sortedSetOf<T>()
        .apply {
            addAll(left)
            addAll(right)
        }

    private fun mergeIssues(
        left: SortedMap<Identifier, List<OrtIssue>>,
        right: SortedMap<Identifier, List<OrtIssue>>
    ): SortedMap<Identifier, List<OrtIssue>> {
        val result = TreeMap<Identifier, List<OrtIssue>>()

        result.putAll(left)

        right.forEach { identifier, issuesList ->
            val mergedList = LinkedList<OrtIssue>()

            result.get(identifier)?.let { mergedList.addAll(it) }
            mergedList.addAll(issuesList)

            result.put(identifier, mergedList)
        }

        return result
    }

    private fun mergeProjects(projects: SortedSet<Project>): Project = projects
        .reduceOrNull { leftProject, rightProject ->
            assertSameVcsInfos(leftProject.vcs, rightProject.vcs)

            Project(
                id = if (leftProject.id != Identifier.EMPTY) leftProject.id else rightProject.id,
                definitionFilePath = firstNotEmptyString(
                    leftProject.definitionFilePath,
                    rightProject.definitionFilePath
                ),
                authors = mergeSortedSets(leftProject.authors, rightProject.authors),
                declaredLicenses = mergeSortedSets(leftProject.declaredLicenses, rightProject.declaredLicenses),
                vcs = leftProject.vcs.merge(rightProject.vcs),
                homepageUrl = firstNotEmptyString(leftProject.homepageUrl, rightProject.homepageUrl),
                scopeDependencies = mergeScopes(leftProject.scopes, rightProject.scopes)
            )
        } ?: Project.EMPTY

    private fun assertSameVcsInfos(leftVcs: VcsInfo, rightVcs: VcsInfo) {
        if (leftVcs.url != rightVcs.url || leftVcs.type != rightVcs.type || leftVcs.revision != rightVcs.revision) {
            throw IllegalArgumentException(
                "Mismatching VCS information for merged analyzer result, "
                        + "left ${leftVcs}, right ${rightVcs}"
            )
        }
    }

    private fun mergeScopes(leftScopes: SortedSet<Scope>, rightScopes: SortedSet<Scope>): SortedSet<Scope> {
        val scopeMap = HashMap<String, Scope>()

        leftScopes.forEach { scope -> scopeMap.put(scope.name, scope) }

        rightScopes.forEach { scope ->
            scopeMap.merge(
                scope.name, scope,
                { left, right ->
                    Scope(
                        name = left.name,
                        dependencies = mergeSortedSets(
                            flattenDepdendencies(left.dependencies),
                            flattenDepdendencies(right.dependencies)
                        )
                    )
                }
            )
        }

        val resultScopes = TreeSet<Scope>()

        resultScopes.addAll(scopeMap.values)

        return resultScopes
    }

    private fun <T : Comparable<T>> wrapValueInSortedSet(value: T): SortedSet<T> {
        val set = TreeSet<T>()

        set.add(value)

        return set
    }

    private fun firstNotEmptyString(left: String, right: String) = left.ifEmpty { right }

    private fun flattenDepdendencies(dependencyTrees: SortedSet<PackageReference>): SortedSet<PackageReference> {
        fun flattenDependenciesInternal(treeNode: PackageReference): SortedSet<PackageReference> {
            val resultSet = TreeSet<PackageReference>()

            resultSet.add(treeNode)

            treeNode.dependencies.forEach { dependency -> resultSet.addAll(flattenDependenciesInternal(dependency)) }
            treeNode.dependencies.clear()

            return resultSet
        }

        val resultSet = TreeSet<PackageReference>()

        dependencyTrees.forEach { dependency -> resultSet.addAll(flattenDependenciesInternal(dependency)) }

        return resultSet
    }
}
