/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonPropertyOrder

import java.lang.invoke.MethodHandles

import org.apache.logging.log4j.kotlin.loggerOf

import org.ossreviewtoolkit.model.DependencyNavigator.Companion.MATCH_SUB_PROJECTS
import org.ossreviewtoolkit.model.ResolvedPackageCurations.Companion.REPOSITORY_CONFIGURATION_PROVIDER_ID
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.Includes
import org.ossreviewtoolkit.model.config.IssueResolution
import org.ossreviewtoolkit.model.config.LicenseFindingCuration
import org.ossreviewtoolkit.model.config.PackageConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.Resolutions
import org.ossreviewtoolkit.model.config.RuleViolationResolution
import org.ossreviewtoolkit.model.config.VulnerabilityResolution
import org.ossreviewtoolkit.model.config.orEmpty
import org.ossreviewtoolkit.model.utils.ResolutionProvider
import org.ossreviewtoolkit.model.vulnerabilities.Vulnerability
import org.ossreviewtoolkit.utils.common.zipWithSets
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseChoice

/**
 * The common output format for the analyzer and scanner. It contains information about the scanned repository, and the
 * analyzer and scanner will add their result to it.
 */
@Suppress("TooManyFunctions")
data class OrtResult(
    /**
     * Information about the repository that was used as input.
     */
    val repository: Repository,

    /**
     * An [AnalyzerRun] containing details about the analyzer that was run using [repository] as input. Can be null
     * if the [repository] was not yet analyzed.
     */
    val analyzer: AnalyzerRun? = null,

    /**
     * A [ScannerRun] containing details about the scanner that was run using the result from [analyzer] as input.
     * Can be null if no scanner was run.
     */
    val scanner: ScannerRun? = null,

    /**
     * An [AdvisorRun] containing details about the advisor that was run using the result from [analyzer] as input.
     * Can be null if no advisor was run.
     */
    val advisor: AdvisorRun? = null,

    /**
     * An [EvaluatorRun] containing details about the evaluation that was run using the result from [scanner] as
     * input. Can be null if no evaluation was run.
     */
    val evaluator: EvaluatorRun? = null,

    /**
     * A [ResolvedConfiguration] containing data resolved during the analysis which augments the automatically
     * determined data.
     */
    val resolvedConfiguration: ResolvedConfiguration = ResolvedConfiguration(),

    /**
     * User defined labels associated to this result. Labels are not used by ORT itself, but can be used in parts of ORT
     * which are customizable by the user, for example in evaluator rules or in the notice reporter.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonPropertyOrder(alphabetic = true)
    val labels: Map<String, String> = emptyMap()
) : ResolutionProvider {
    companion object {
        /**
         * A constant for an [OrtResult] with an empty repository and all other properties `null`.
         */
        @JvmField
        val EMPTY = OrtResult(
            repository = Repository.EMPTY,
            analyzer = null,
            scanner = null,
            advisor = null,
            evaluator = null,
            labels = emptyMap()
        )
    }

    /** An object that can be used to navigate the dependency information contained in this result. */
    @get:JsonIgnore
    val dependencyNavigator: DependencyNavigator by lazy { CompatibilityDependencyNavigator.create(this) }

    private val advisorResultsById: Map<Identifier, List<AdvisorResult>> by lazy {
        advisor?.results.orEmpty()
    }

    /**
     * A map of packages and their excluded state. Calculating this map once brings massive performance improvements
     * when querying packages in large analyzer results. This map can also contain projects if they appear as
     * dependencies of other projects, but for them only the excluded state is provided, no [CuratedPackage] instance.
     */
    private val packages: Map<Identifier, PackageEntry> by lazy {
        val projects = getProjects()
        val packages = analyzer?.result?.packages.orEmpty().associateBy { it.id }
        val curatedPackages = applyPackageCurations(
            packages.values,
            resolvedConfiguration.getAllPackageCurations()
        ).associateBy { it.metadata.id }

        val allDependencies = packages.keys.toMutableSet()
        val includedDependencies = mutableSetOf<Identifier>()

        projects.forEach { project ->
            dependencyNavigator.scopeNames(project).forEach { scopeName ->
                dependencyNavigator.scopeDependencies(project, scopeName).forEach { dependencies ->
                    val isScopeExcluded = getExcludes().isScopeExcluded(scopeName)
                    allDependencies += dependencies

                    if (!isProjectExcluded(project.id) && !isScopeExcluded) {
                        includedDependencies += dependencies
                    }
                }
            }
        }

        allDependencies.associateWithTo(mutableMapOf()) { id ->
            PackageEntry(
                pkg = packages[id],
                curatedPackage = curatedPackages[id],
                isExcluded = id !in includedDependencies
            )
        }
    }

    /**
     * Store all resolve package configurations by their ID for faster lookup. The version is ignored because it could
     * be a version range.
     */
    private val packageConfigurationsByIdWithoutVersion: Map<Identifier, List<PackageConfiguration>> by lazy {
        resolvedConfiguration.packageConfigurations.orEmpty().groupBy { it.id.copy(version = "") }
    }

    private val issuesWithExcludedAffectedPathById: Map<Identifier, Set<Issue>> by lazy {
        buildMap<Identifier, MutableSet<Issue>> {
            scanner?.getAllScanResults().orEmpty().forEach { (id, scanResults) ->
                scanResults.forEach { scanResult ->
                    val pathExcludes = getPathExcludes(id, scanResult.provenance)

                    scanResult.summary.issues.forEach { issue ->
                        if (issue.affectedPath != null && pathExcludes.any { it.matches(issue.affectedPath) }) {
                            getOrPut(id) { mutableSetOf() } += issue
                        }
                    }
                }
            }
        }
    }

    private fun getPathExcludes(id: Identifier, provenance: Provenance) =
        if (isProject(id)) {
            repository.config.excludes.paths
        } else {
            getPackageConfigurations(id, provenance).flatMapTo(mutableSetOf()) { it.pathExcludes }
        }

    /**
     * A map of projects and their excluded state. Calculating this map once brings massive performance improvements
     * when querying projects in large analyzer results.
     */
    private val projects: Map<Identifier, ProjectEntry> by lazy {
        getProjects().associateBy(
            { project -> project.id },
            { project ->
                val pathExcludes = getExcludes().findPathExcludes(project, this)
                val pathIncludes = getIncludes().findPathIncludes(project, this)

                val isExcluded = if (getIncludes() == Includes.EMPTY) {
                    // No includes are defined. It is excluded if it has path excludes.
                    pathExcludes.isNotEmpty()
                } else {
                    // Some includes are defined. It is excluded if it has no path includes or has path excludes.
                    pathIncludes.isEmpty() || pathExcludes.isNotEmpty()
                }

                ProjectEntry(
                    project = project,
                    isExcluded = isExcluded
                )
            }
        )
    }

    private val relativeProjectVcsPath: Map<Identifier, String?> by lazy {
        getProjects().associateBy({ it.id }, { repository.getRelativePath(it.vcsProcessed) })
    }

    /**
     * Return all [AdvisorResult]s contained in this [OrtResult] or only the non-excluded ones if [omitExcluded] is
     * true.
     */
    @JsonIgnore
    fun getAdvisorResults(omitExcluded: Boolean = false): Map<Identifier, List<AdvisorResult>> =
        advisorResultsById.filter { (id, _) ->
            !omitExcluded || !isExcluded(id)
        }

    /**
     * Return the list of [AdvisorResult]s for the given [id].
     */
    @Suppress("unused")
    fun getAdvisorResultsForId(id: Identifier): List<AdvisorResult> = advisorResultsById[id].orEmpty()

    /**
     * Return the path of the definition file of the [project], relative to the analyzer root. If the project was
     * checked out from a VCS the analyzer root is the root of the working tree, if the project was not checked out from
     * a VCS the analyzer root is the input directory of the analyzer.
     */
    fun getDefinitionFilePathRelativeToAnalyzerRoot(project: Project) =
        getFilePathRelativeToAnalyzerRoot(project, project.definitionFilePath)

    /**
     * Return the dependencies of the given [id] (which can refer to a [Project] or a [Package]), up to and including a
     * depth of [maxLevel] where counting starts at 0 (for the [Project] or [Package] itself) and 1 are direct
     * dependencies etc. A value below 0 means to not limit the depth. If [omitExcluded] is set to true, identifiers of
     * excluded projects / packages are omitted from the result.
     */
    fun getDependencies(id: Identifier, maxLevel: Int = -1, omitExcluded: Boolean = false): Set<Identifier> {
        val dependencies = mutableSetOf<Identifier>()
        val matcher = DependencyNavigator.MATCH_ALL.takeUnless { omitExcluded } ?: { !isExcluded(it.id) }

        getProjects().forEach { project ->
            if (project.id == id) {
                dependencies += dependencyNavigator.projectDependencies(project, maxLevel, matcher)
            }

            dependencies += dependencyNavigator.packageDependencies(project, id, maxLevel, matcher)
        }

        return dependencies
    }

    @JsonIgnore
    fun getIncludes(): Includes = repository.config.includes

    @JsonIgnore
    fun getExcludes(): Excludes = repository.config.excludes

    /**
     * Return the path of a file contained in [project], relative to the analyzer root. If the project was checked out
     * from a VCS the analyzer root is the root of the working tree, if the project was not checked out from a VCS the
     * analyzer root is the input directory of the analyzer.
     */
    fun getFilePathRelativeToAnalyzerRoot(project: Project, path: String): String {
        val vcsPath = relativeProjectVcsPath.getValue(project.id)

        requireNotNull(vcsPath) {
            "The ${project.vcsProcessed} of project '${project.id.toCoordinates()}' cannot be found in $repository."
        }

        return buildString {
            if (vcsPath.isNotEmpty()) {
                append(vcsPath)
                append("/")
            }

            append(path)
        }
    }

    /**
     * Return a map of all de-duplicated [Issue]s associated by [Identifier]. If [omitExcluded] is set to true, excluded
     * issues are omitted from the result. If [omitResolved] is set to true, resolved issues are omitted from the
     * result. Issues with [severity][Issue.severity] below [minSeverity] are omitted from the result.
     */
    @JsonIgnore
    fun getIssues(
        omitExcluded: Boolean = false,
        omitResolved: Boolean = false,
        minSeverity: Severity = Severity.entries.min()
    ): Map<Identifier, Set<Issue>> =
        getAnalyzerIssues()
            .zipWithSets(getScannerIssues())
            .zipWithSets(getAdvisorIssues())
            .filterIssues(omitExcluded, omitResolved, minSeverity)

    /**
     * Return a map of all de-duplicated analyzer [Issue]s associated by [Identifier]. If [omitExcluded] is set to true,
     * excluded issues are omitted from the result. If [omitResolved] is set to true, resolved issues are omitted from
     * the result. Issues with [severity][Issue.severity] below [minSeverity] are omitted from the result.
     */
    fun getAnalyzerIssues(
        omitExcluded: Boolean = false,
        omitResolved: Boolean = false,
        minSeverity: Severity = Severity.entries.min()
    ): Map<Identifier, Set<Issue>> =
        analyzer?.result?.getAllIssues().orEmpty().filterIssues(omitExcluded, omitResolved, minSeverity)

    /**
     * Return a map of all de-duplicated scanner [Issue]s associated by [Identifier]. If [omitExcluded] is set to true,
     * excluded issues are omitted from the result. If [omitResolved] is set to true, resolved issues are omitted from
     * the result. Issues with [severity][Issue.severity] below [minSeverity] are omitted from the result.
     */
    fun getScannerIssues(
        omitExcluded: Boolean = false,
        omitResolved: Boolean = false,
        minSeverity: Severity = Severity.entries.min()
    ): Map<Identifier, Set<Issue>> =
        scanner?.getAllIssues().orEmpty().filterIssues(omitExcluded, omitResolved, minSeverity)

    /**
     * Return a map of all de-duplicated advisor [Issue]s associated by [Identifier]. If [omitExcluded] is set to true,
     * excluded issues are omitted from the result. If [omitResolved] is set to true, resolved issues are omitted from
     * the result. Issues with [severity][Issue.severity] below [minSeverity] are omitted from the result.
     */
    fun getAdvisorIssues(
        omitExcluded: Boolean = false,
        omitResolved: Boolean = false,
        minSeverity: Severity = Severity.entries.min()
    ): Map<Identifier, Set<Issue>> =
        advisor?.getIssues().orEmpty().filterIssues(omitExcluded, omitResolved, minSeverity)

    private fun Map<Identifier, Set<Issue>>.filterIssues(
        omitExcluded: Boolean = false,
        omitResolved: Boolean = false,
        minSeverity: Severity = Severity.entries.min()
    ): Map<Identifier, Set<Issue>> =
        mapNotNull { (id, issues) ->
            if (omitExcluded && isExcluded(id)) return@mapNotNull null

            val filteredIssues = issues.filterTo(mutableSetOf()) {
                (!omitResolved || !isResolved(it))
                    && it.severity >= minSeverity
                    && (!omitExcluded || it !in issuesWithExcludedAffectedPathById[id].orEmpty())
            }

            filteredIssues.takeUnless { it.isEmpty() }?.let { id to it }
        }.toMap()

    /**
     * Return the label values corresponding to the given [key] split at the delimiter ',', or an empty set if the label
     * is absent.
     */
    fun getLabelValues(key: String): Set<String> = labels[key]?.split(',').orEmpty().mapTo(mutableSetOf()) { it.trim() }

    /**
     * Return the [LicenseFindingCuration]s associated with the given package [id].
     */
    fun getLicenseFindingCurations(id: Identifier): List<LicenseFindingCuration> =
        if (projects.containsKey(id)) {
            repository.config.curations.licenseFindings
        } else {
            emptyList()
        }

    /**
     * Return all non-excluded issues which are not resolved by resolutions in the resolved configuration of this
     * [OrtResult] with severities equal to or over [minSeverity].
     */
    @JsonIgnore
    fun getOpenIssues(minSeverity: Severity = Severity.WARNING) =
        getIssues(omitExcluded = true, omitResolved = true, minSeverity = minSeverity).values.flatten().distinct()

    /**
     * Return a list of [PackageConfiguration]s for the given [packageId] and [provenance].
     */
    fun getPackageConfigurations(packageId: Identifier, provenance: Provenance): List<PackageConfiguration> =
        packageConfigurationsByIdWithoutVersion[packageId.copy(version = "")].orEmpty()
            .filter { it.matches(packageId, provenance) }

    /**
     * Return all projects and packages that are likely to belong to one of the organizations of the given [names]. If
     * [omitExcluded] is set to true, excluded projects / packages are omitted from the result. Projects are converted
     * to packages in the result. If no analyzer result is present an empty set is returned.
     */
    @Suppress("unused") // This is intended to be mostly used via scripting.
    fun getOrgPackages(vararg names: String, omitExcluded: Boolean = false): Set<Package> {
        val vendorPackages = mutableSetOf<Package>()

        getProjects().filter {
            it.id.isFromOrg(*names) && (!omitExcluded || !isExcluded(it.id))
        }.mapTo(vendorPackages) {
            it.toPackage()
        }

        getPackages().filter { (pkg, _) ->
            pkg.id.isFromOrg(*names) && (!omitExcluded || !isPackageExcluded(pkg.id))
        }.mapTo(vendorPackages) {
            it.metadata
        }

        return vendorPackages
    }

    /**
     * Return the [CuratedPackage] denoted by the given [id].
     */
    fun getPackage(id: Identifier): CuratedPackage? = packages[id]?.curatedPackage

    /**
     * Return all [SpdxLicenseChoice]s for the [Package] with [id].
     */
    fun getPackageLicenseChoices(id: Identifier): List<SpdxLicenseChoice> =
        repository.config.licenseChoices.packageLicenseChoices.find { it.packageId == id }?.licenseChoices.orEmpty()

    /**
     * Return a [CuratedPackage] which represents either a [Package] if the given [id] corresponds to a [Package],
     * a [Project] if the given [id] corresponds to a [Project] or `null` otherwise.
     */
    fun getPackageOrProject(id: Identifier): CuratedPackage? =
        getPackage(id) ?: getProject(id)?.toPackage()?.toCuratedPackage()

    /**
     * Return all [CuratedPackage]s contained in this [OrtResult] or only the non-excluded ones if [omitExcluded] is
     * true.
     */
    @JsonIgnore
    fun getPackages(omitExcluded: Boolean = false): Set<CuratedPackage> =
        packages.mapNotNullTo(mutableSetOf()) { (_, entry) ->
            entry.curatedPackage.takeUnless { omitExcluded && entry.isExcluded }
        }

    /**
     * Return the [Project] denoted by the given [id].
     */
    fun getProject(id: Identifier): Project? = projects[id]?.project

    /**
     * Return the [Project]s contained in this [OrtResult], optionally limited to only non-excluded ones if
     * [omitExcluded] is true, or to only root projects if [includeSubProjects] is false.
     */
    @JsonIgnore
    fun getProjects(omitExcluded: Boolean = false, includeSubProjects: Boolean = true): Set<Project> {
        val projects = analyzer?.result?.projects.orEmpty().filterTo(mutableSetOf()) { project ->
            !omitExcluded || !isExcluded(project.id)
        }

        if (!includeSubProjects) {
            val subProjectIds = projects.flatMapTo(mutableSetOf()) {
                dependencyNavigator.projectDependencies(it, matcher = MATCH_SUB_PROJECTS)
            }

            projects.removeAll { it.id in subProjectIds }
        }

        return projects
    }

    /**
     * Return the set of all project or package identifiers in the result, optionally [including those of subprojects]
     * [includeSubProjects] and optionally limited to only non-excluded ones if [omitExcluded] is true.
     */
    @JsonIgnore
    fun getProjectsAndPackages(includeSubProjects: Boolean = true, omitExcluded: Boolean = false): Set<Identifier> =
        buildSet {
            getProjects(includeSubProjects = includeSubProjects, omitExcluded = omitExcluded).mapTo(this) { it.id }
            getPackages(omitExcluded = omitExcluded).mapTo(this) { it.metadata.id }
        }

    /**
     * Return all [SpdxLicenseChoice]s applicable for the scope of the whole [repository].
     */
    @JsonIgnore
    fun getRepositoryLicenseChoices(): List<SpdxLicenseChoice> =
        repository.config.licenseChoices.repositoryLicenseChoices

    /**
     * Return the [Resolutions] contained in the repository configuration of this [OrtResult].
     */
    @JsonIgnore
    fun getRepositoryConfigResolutions(): Resolutions = repository.config.resolutions.orEmpty()

    /**
     * Return the [Resolutions] contained in the resolved configuration of this [OrtResult].
     */
    @JsonIgnore
    fun getResolutions(): Resolutions = resolvedConfiguration.resolutions.orEmpty()

    /**
     * Return true if and only if [violation] is resolved in this [OrtResult].
     */
    override fun isResolved(violation: RuleViolation): Boolean =
        getResolutions().ruleViolations.any { it.matches(violation) }

    /**
     * Return true if and only if [vulnerability] is resolved in this [OrtResult].
     */
    override fun isResolved(vulnerability: Vulnerability): Boolean =
        getResolutions().vulnerabilities.any { it.matches(vulnerability) }

    /**
     * Return the resolutions matching [issue].
     */
    override fun getResolutionsFor(issue: Issue): List<IssueResolution> =
        getResolutions().issues.filter { it.matches(issue) }

    /**
     * Return the resolutions matching [violation].
     */
    override fun getResolutionsFor(violation: RuleViolation): List<RuleViolationResolution> =
        getResolutions().ruleViolations.filter { it.matches(violation) }

    /**
     * Return the resolutions matching [vulnerability].
     */
    override fun getResolutionsFor(vulnerability: Vulnerability): List<VulnerabilityResolution> =
        getResolutions().vulnerabilities.filter { it.matches(vulnerability) }

    /**
     * Return all [RuleViolation]s contained in this [OrtResult]. Optionally exclude resolved violations with
     * [omitResolved] and remove violations below the [minSeverity].
     */
    @JsonIgnore
    fun getRuleViolations(
        omitResolved: Boolean = false,
        minSeverity: Severity = Severity.entries.min()
    ): List<RuleViolation> =
        evaluator?.violations.orEmpty().filter {
            (!omitResolved || !isResolved(it)) && it.severity >= minSeverity
        }

    /**
     * Return the list of [ScanResult]s for the given [id].
     */
    fun getScanResultsForId(id: Identifier): List<ScanResult> = scanner?.getScanResults(id).orEmpty()

    /**
     * Return the scan results associated with the respective identifiers.
     */
    @JsonIgnore
    fun getScanResults(): Map<Identifier, List<ScanResult>> = scanner?.getAllScanResults().orEmpty()

    /**
     * Return the [FileList] for the given [id].
     */
    fun getFileListForId(id: Identifier): FileList? = scanner?.getFileList(id)

    /**
     * Return the [FileList] associated with the respective identifier.
     */
    @JsonIgnore
    fun getFileLists(): Map<Identifier, FileList> = scanner?.getAllFileLists().orEmpty()

    /**
     * Return an uncurated [Package] which represents either a [Package] if the given [id] corresponds to a [Package],
     * a [Project] if the given [id] corresponds to a [Project] or `null` otherwise.
     */
    fun getUncuratedPackageOrProject(id: Identifier): Package? = packages[id]?.pkg ?: getProject(id)?.toPackage()

    /**
     * Return all uncurated [Package]s contained in this [OrtResult] or only the non-excluded ones if [omitExcluded] is
     * true.
     */
    @JsonIgnore
    fun getUncuratedPackages(omitExcluded: Boolean = false): Set<Package> =
        packages.mapNotNullTo(mutableSetOf()) { (_, entry) ->
            entry.pkg.takeUnless { omitExcluded && entry.isExcluded }
        }

    @JsonIgnore
    fun getVulnerabilities(
        omitResolved: Boolean = false,
        omitExcluded: Boolean = false
    ): Map<Identifier, List<Vulnerability>> {
        val allVulnerabilities = advisor?.getVulnerabilities().orEmpty()
            .filterKeys { !omitExcluded || !isExcluded(it) }

        return if (omitResolved) {
            allVulnerabilities.mapValues { (_, vulnerabilities) ->
                vulnerabilities.filter { !isResolved(it) }
            }.filterValues { it.isNotEmpty() }
        } else {
            allVulnerabilities
        }
    }

    /**
     * Return true if a [label] with [value] exists in this [OrtResult]. If [value] is null the value of the label is
     * ignored. If [splitValue] is true, the label value is interpreted as comma-separated list.
     */
    fun hasLabel(label: String, value: String? = null, splitValue: Boolean = true) =
        if (value == null) {
            label in labels
        } else if (splitValue) {
            value in getLabelValues(label)
        } else {
            labels[label] == value
        }

    /**
     * Return `true` if the project or package with the given [id] is excluded.
     *
     * If the [id] references a [Project] it is seen as excluded if the project itself is excluded and also all
     * dependencies on this project in other projects are excluded.
     *
     * If the [id] references a [Package] it is seen as excluded if all dependencies on this package are excluded.
     *
     * Return `false` if there is no project or package with this [id].
     */
    fun isExcluded(id: Identifier): Boolean =
        if (isProject(id)) {
            // An excluded project could still be included as a dependency of another non-excluded project.
            isProjectExcluded(id) && (id !in packages || isPackageExcluded(id))
        } else {
            isPackageExcluded(id)
        }

    /**
     * Return `true` if and only if the given [issue] is excluded in context of the given [id]. This is the case when
     * either [id] is excluded, or the [affected path][Issue.affectedPath] of [issue] is matched by a path exclude.
     */
    fun isExcluded(issue: Issue, id: Identifier): Boolean =
        isExcluded(id) || issue in issuesWithExcludedAffectedPathById[id].orEmpty()

    /**
     * Return `true` if all dependencies on the package or project identified by the given [id] are excluded. This is
     * the case if all [Project]s or [Scope]s that have a dependency on this [id] are excluded.
     *
     * If the [id] references a [Project] it is only checked if all dependencies on this project are excluded, not if
     * the project itself is excluded. If you need to check that also the project itself is excluded use [isExcluded]
     * instead.
     *
     * Return `false` if there is no dependency on this [id].
     */
    fun isPackageExcluded(id: Identifier): Boolean = packages[id]?.isExcluded == true

    /**
     * Return `true` if the [Project] with the given [id] is excluded.
     *
     * This function only checks if the project itself is excluded, not if another non-excluded project has a dependency
     * on this project. If you need to check that also all dependencies on this project are excluded use [isExcluded]
     * instead.
     *
     * Return `false` if no project with the given [id] is found.
     */
    fun isProjectExcluded(id: Identifier): Boolean = projects[id]?.isExcluded == true

    /**
     * Return true if and only if the given [id] denotes a [Package] contained in this [OrtResult].
     */
    fun isPackage(id: Identifier): Boolean = getPackage(id) != null

    /**
     * Return true if and only if the given [id] denotes a [Project] contained in this [OrtResult].
     */
    fun isProject(id: Identifier): Boolean = getProject(id) != null

    /**
     * Return a copy of this [OrtResult] with the [Repository.config] replaced by [config]. The package curations
     * within the given config only take effect in case the corresponding feature was enabled during the initial
     * creation of this [OrtResult].
     */
    fun replaceConfig(config: RepositoryConfiguration): OrtResult =
        copy(
            repository = repository.copy(
                config = config
            ),
            resolvedConfiguration = resolvedConfiguration.copy(
                packageCurations = resolvedConfiguration.packageCurations.map {
                    if (it.provider.id != REPOSITORY_CONFIGURATION_PROVIDER_ID) {
                        it
                    } else {
                        it.copy(curations = config.curations.packages)
                    }
                }
            )
        )
}

private val logger = loggerOf(MethodHandles.lookup().lookupClass())

/**
 * Return a set containing exactly one [CuratedPackage] for each given [Package], derived from applying all
 * given [curations] to the packages they apply to. The given [curations] must be ordered highest-priority-first, which
 * is the inverse order of their application.
 */
internal fun applyPackageCurations(
    packages: Collection<Package>,
    curations: List<PackageCuration>
): Set<CuratedPackage> {
    val curationsForId = packages.associate { pkg ->
        pkg.id to curations.filter { it.isApplicable(pkg.id) }
    }

    return packages.mapTo(mutableSetOf()) { pkg ->
        curationsForId[pkg.id].orEmpty().asReversed().fold(pkg.toCuratedPackage()) { cur, packageCuration ->
            logger.debug {
                "Applying curation '$packageCuration' to package '${pkg.id.toCoordinates()}'."
            }

            packageCuration.apply(cur)
        }
    }
}

private data class PackageEntry(
    val pkg: Package?,
    val curatedPackage: CuratedPackage?,
    val isExcluded: Boolean
)

private data class ProjectEntry(val project: Project, val isExcluded: Boolean)
