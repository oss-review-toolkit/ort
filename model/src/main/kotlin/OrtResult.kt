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

import org.apache.logging.log4j.kotlin.Logging

import org.ossreviewtoolkit.model.ResolvedPackageCurations.Companion.REPOSITORY_CONFIGURATION_PROVIDER_ID
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.LicenseFindingCuration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.Resolutions
import org.ossreviewtoolkit.model.config.orEmpty
import org.ossreviewtoolkit.utils.common.zipWithCollections
import org.ossreviewtoolkit.utils.spdx.model.SpdxLicenseChoice

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
    val labels: Map<String, String> = emptyMap()
) {
    companion object : Logging {
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
        advisor?.results?.advisorResults.orEmpty()
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
            dependencyNavigator.scopeDependencies(project).forEach { (scopeName, dependencies) ->
                val isScopeExcluded = getExcludes().isScopeExcluded(scopeName)
                allDependencies += dependencies

                if (!isProjectExcluded(project.id) && !isScopeExcluded) {
                    includedDependencies += dependencies
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
     * A map of projects and their excluded state. Calculating this map once brings massive performance improvements
     * when querying projects in large analyzer results.
     */
    private val projects: Map<Identifier, ProjectEntry> by lazy {
        getProjects().associateBy(
            { project -> project.id },
            { project ->
                val pathExcludes = getExcludes().findPathExcludes(project, this)
                ProjectEntry(
                    project = project,
                    isExcluded = pathExcludes.isNotEmpty()
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
    @Suppress("UNUSED")
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
     * dependencies etc. A value below 0 means to not limit the depth.
     */
    fun getDependencies(id: Identifier, maxLevel: Int = -1): Set<Identifier> {
        val dependencies = mutableSetOf<Identifier>()

        getProjects().forEach { project ->
            if (project.id == id) {
                dependencies += dependencyNavigator.projectDependencies(project, maxLevel)
            }

            dependencies += dependencyNavigator.packageDependencies(project, id, maxLevel)
        }

        return dependencies
    }

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
     * Return a map of all de-duplicated [Issue]s associated by [Identifier].
     */
    @JsonIgnore
    fun getIssues(): Map<Identifier, Set<Issue>> {
        val analyzerIssues = analyzer?.result?.getAllIssues().orEmpty()
        val scannerIssues = scanner?.getIssues().orEmpty()
        val advisorIssues = advisor?.results?.getIssues().orEmpty()

        val analyzerAndScannerIssues = analyzerIssues.zipWithCollections(scannerIssues)
        return analyzerAndScannerIssues.zipWithCollections(advisorIssues)
    }

    /**
     * Return the label values corresponding to the given [key] split at the delimiter ',', or an empty set if the label
     * is absent.
     */
    fun getLabelValues(key: String): Set<String> =
        labels[key]?.split(',').orEmpty().mapTo(mutableSetOf()) { it.trim() }

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
     * Retrieve non-excluded issues which are not resolved by resolutions in the repository configuration of this
     * [OrtResult] with severities equal to or over [minSeverity].
     */
    @JsonIgnore
    fun getOpenIssues(minSeverity: Severity = Severity.WARNING) = getIssues()
        .mapNotNull { (id, issues) -> issues.takeUnless { isExcluded(id) } }
        .flatten()
        .filter { issue -> issue.severity >= minSeverity && getResolutions().issues.none { it.matches(issue) } }

    /**
     * Return all projects and packages that are likely to belong to one of the organizations of the given [names]. If
     * [omitExcluded] is set to true, excluded projects / packages are omitted from the result. Projects are converted
     * to packages in the result. If no analyzer result is present an empty set is returned.
     */
    @Suppress("UNUSED") // This is intended to be mostly used via scripting.
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
                dependencyNavigator.collectSubProjects(it)
            }

            projects.removeAll { it.id in subProjectIds }
        }

        return projects
    }

    /**
     * Return the set of all project or package identifiers in the result, optionally [including those of subprojects]
     * [includeSubProjects].
     */
    @JsonIgnore
    fun getProjectsAndPackages(includeSubProjects: Boolean = true): Set<Identifier> {
        val projectsAndPackages = mutableSetOf<Identifier>()
        val projects = getProjects(includeSubProjects = includeSubProjects)

        projects.mapTo(projectsAndPackages) { it.id }
        getPackages().mapTo(projectsAndPackages) { it.metadata.id }

        return projectsAndPackages
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
    fun getResolutions(): Resolutions = repository.config.resolutions.orEmpty()

    /**
     * Return all [RuleViolation]s contained in this [OrtResult]. Optionally exclude resolved violations with
     * [omitResolved] and remove violations below the [minSeverity].
     */
    @JsonIgnore
    fun getRuleViolations(omitResolved: Boolean = false, minSeverity: Severity? = null): List<RuleViolation> {
        val allViolations = evaluator?.violations.orEmpty()

        val severeViolations = when (minSeverity) {
            null -> allViolations
            else -> allViolations.filter { it.severity >= minSeverity }
        }

        return if (omitResolved) {
            val resolutions = getResolutions().ruleViolations

            severeViolations.filter { violation ->
                resolutions.none { resolution ->
                    resolution.matches(violation)
                }
            }
        } else {
            severeViolations
        }
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
    fun getUncuratedPackageOrProject(id: Identifier): Package? =
        packages[id]?.pkg ?: getProject(id)?.toPackage()

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
        val allVulnerabilities = advisor?.results?.getVulnerabilities().orEmpty()
            .filterKeys { !omitExcluded || !isExcluded(it) }

        return if (omitResolved) {
            val resolutions = getResolutions().vulnerabilities

            allVulnerabilities.mapValues { (_, vulnerabilities) ->
                vulnerabilities.filter { vulnerability ->
                    resolutions.none { it.matches(vulnerability) }
                }
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
     * Return `true` if all dependencies on the package or project identified by the given [id] are excluded. This is
     * the case if all [Project]s or [Scope]s that have a dependency on this [id] are excluded.
     *
     * If the [id] references a [Project] it is only checked if all dependencies on this project are excluded, not if
     * the project itself is excluded. If you need to check that also the project itself is excluded use [isExcluded]
     * instead.
     *
     * Return `false` if there is no dependency on this [id].
     */
    fun isPackageExcluded(id: Identifier): Boolean = packages[id]?.isExcluded ?: false

    /**
     * Return `true` if the [Project] with the given [id] is excluded.
     *
     * This function only checks if the project itself is excluded, not if another non-excluded project has a dependency
     * on this project. If you need to check that also all dependencies on this project are excluded use [isExcluded]
     * instead.
     *
     * Return `false` if no project with the given [id] is found.
     */
    fun isProjectExcluded(id: Identifier): Boolean = projects[id]?.isExcluded ?: false

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

    /**
     * Resolves the scopes of all [Project]s in this [OrtResult] with [Project.withResolvedScopes].
     */
    fun withResolvedScopes(): OrtResult =
        copy(
            analyzer = analyzer?.copy(
                result = analyzer.result.withResolvedScopes()
            )
        )
}

/**
 * Return a set containing exactly one [CuratedPackage] for each given [Package], derived from applying all
 * given [curations] to the packages they apply to. The given [curations] must be ordered highest-priority-first, which
 * is the inverse order of their application.
 */
private fun applyPackageCurations(
    packages: Collection<Package>,
    curations: List<PackageCuration>
): Set<CuratedPackage> {
    val curationsForId = packages.associateBy(
        keySelector = { pkg -> pkg.id },
        valueTransform = { pkg ->
            curations.filter { curation ->
                curation.isApplicable(pkg.id)
            }
        }
    )

    return packages.mapTo(mutableSetOf()) { pkg ->
        curationsForId[pkg.id].orEmpty().asReversed().fold(pkg.toCuratedPackage()) { cur, packageCuration ->
            OrtResult.logger.debug {
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
