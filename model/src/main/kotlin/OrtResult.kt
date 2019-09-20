/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package com.here.ort.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude

import com.here.ort.model.config.Excludes
import com.here.ort.model.config.PathExclude
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.model.config.Resolutions
import com.here.ort.model.config.orEmpty
import com.here.ort.spdx.SpdxExpression
import com.here.ort.utils.log
import com.here.ort.utils.zipWithDefault

import java.util.SortedSet

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
     * An [EvaluatorRun] containing details about the evaluation that was run using the result from [scanner] as
     * input. Can be null if no evaluation was run.
     */
    val evaluator: EvaluatorRun? = null
) {
    companion object {
        /**
         * A constant for an [OrtResult] with an empty repository an all other properties `null`.
         */
        @JvmField
        val EMPTY = OrtResult(
            repository = Repository.EMPTY,
            analyzer = null,
            scanner = null,
            evaluator = null
        )
    }

    /**
     * A map that holds arbitrary data. Can be used by third-party tools to add custom data to the model.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val data: CustomData = mutableMapOf()

    private data class ProjectEntry(val project: Project, val isExcluded: Boolean)

    private val projects: Map<Identifier, ProjectEntry> by lazy {
        log.info { "Computing excluded projects which may take a while..." }

        val result = getProjects().associateBy(
            { project -> project.id },
            { project ->
                val pathExcludes = getExcludes().findPathExcludes(project, this)
                ProjectEntry(
                    project = project,
                    isExcluded = pathExcludes.isNotEmpty()
                )
            }
        )

        log.info { "Computing excluded projects done." }
        result
    }

    private data class PackageEntry(val curatedPackage: CuratedPackage, val isExcluded: Boolean)

    private val packages: Map<Identifier, PackageEntry> by lazy {
        log.info { "Computing excluded packages which may take a while..." }

        val includedPackages = mutableSetOf<Identifier>()
        getProjects().forEach { project ->
            project.scopes.forEach { scope ->
                val isScopeExcluded = getExcludes().isScopeExcluded(scope)

                if (!isProjectExcluded(project.id) && !isScopeExcluded) {
                    val dependencies = scope.collectDependencies().map { it.id }
                    includedPackages.addAll(dependencies)
                }
            }
        }

        val result = getPackages().associateBy(
            { curatedPackage -> curatedPackage.pkg.id },
            { curatedPackage ->
                PackageEntry(
                    curatedPackage = curatedPackage,
                    isExcluded = !includedPackages.contains(curatedPackage.pkg.id)
                )
            }
        )

        log.info { "Computing excluded packages done." }
        result
    }

    /**
     * Return the dependencies of the given [id] (which can refer to a [Project] or a [Package]), up to and including a
     * depth of [maxLevel] where counting starts at 0 (for the [Project] or [Package] itself) and 1 are direct
     * dependencies etc. A value below 0 means to not limit the depth.
     */
    fun collectDependencies(id: Identifier, maxLevel: Int = -1): SortedSet<PackageReference> {
        val dependencies = sortedSetOf<PackageReference>()

        getProjects().forEach { project ->
            if (project.id == id) {
                dependencies += project.collectDependencies(maxLevel)
            }

            project.findReferences(id).forEach { ref ->
                dependencies += ref.collectDependencies(maxLevel)
            }
        }

        return dependencies
    }

    /**
     * Return a map of all de-duplicated errors associated by [Identifier].
     */
    fun collectErrors(): Map<Identifier, Set<OrtIssue>> {
        val analyzerErrors = analyzer?.result?.collectErrors() ?: emptyMap()
        val scannerErrors = scanner?.results?.collectErrors() ?: emptyMap()
        return analyzerErrors.zipWithDefault(scannerErrors, emptySet()) { left, right -> left + right }
    }

    /**
     * Return a map of license findings for each project or package [Identifier]. The license findings for projects are
     * mapped to a list of [PathExclude]s matching the locations where a license was found. This list is only populated
     * if all file locations are excluded. The list is empty for all dependency packages, as path excludes are only
     * applied to the projects.
     *
     * If [omitExcluded] is set to true, excluded projects / packages are omitted from the result.
     */
    fun collectLicenseFindings(omitExcluded: Boolean = false) =
        sortedMapOf<Identifier, MutableMap<LicenseFindings, List<PathExclude>>>().also { findings ->
            val excludes = getExcludes()

            scanner?.results?.scanResults?.forEach { result ->
                val project = getProject(result.id)

                if (!omitExcluded || !isPackageExcluded(result.id)) {
                    result.results.flatMap { it.summary.groupedLicenseFindings }.forEach { finding ->
                        val matchingExcludes = mutableSetOf<PathExclude>()

                        // Only license findings of projects can be excluded by path excludes.
                        val isExcluded = project != null && finding.locations.all { location ->
                            val path = getFilePathRelativeToAnalyzerRoot(project, location.path)
                            excludes.paths.any { exclude ->
                                exclude.matches(path)
                                    .also { matches -> if (matches) matchingExcludes += exclude }
                            }
                        }

                        // TODO: Also filter copyrights excluded by path excludes.

                        findings.getOrPut(result.id) { mutableMapOf() }[finding] =
                                // Only add matching excludes if all license locations are excluded.
                            if (isExcluded) matchingExcludes.toList() else emptyList()
                    }
                }
            }
        }

    /**
     * Return the set of all project or package identifiers in the result, optionally [including those of sub-projects]
     * [includeSubProjects].
     */
    fun collectProjectsAndPackages(includeSubProjects: Boolean = true): SortedSet<Identifier> {
        val projectsAndPackages = sortedSetOf<Identifier>()
        val allSubProjects = sortedSetOf<Identifier>()

        if (!includeSubProjects) {
            getProjects().forEach {
                it.collectSubProjects().mapTo(allSubProjects) { ref -> ref.id }
            }
        }

        getProjects().mapNotNullTo(projectsAndPackages) { project ->
            project.id.takeUnless { it in allSubProjects }
        }

        getPackages().mapTo(projectsAndPackages) { it.pkg.id }

        return projectsAndPackages
    }

    /**
     * Return the concluded license for the given package [id], or null if there is no concluded license.
     */
    fun getConcludedLicensesForId(id: Identifier): SpdxExpression? =
        getPackage(id)?.pkg?.concludedLicense

    /**
     * Return the processed declared licenses for the given [id] which may either refer to a project or to a package. If
     * [id] is not found an empty set is returned.
     */
    fun getDeclaredLicensesForId(id: Identifier): SortedSet<String> =
        getProject(id)?.declaredLicensesProcessed?.allLicenses?.toSortedSet()
            ?: getPackage(id)?.pkg?.declaredLicensesProcessed?.allLicenses?.toSortedSet()
            ?: sortedSetOf()

    /**
     * Return all detected licenses for the given package [id]. As projects are implicitly converted to packages before
     * scanning, the [id] may either refer to a project or to a package. If [id] is not found an empty set is returned.
     */
    @Suppress("UNUSED") // This is intended to be mostly used via scripting.
    fun getDetectedLicensesForId(id: Identifier) =
        scanner?.results?.scanResults?.find { it.id == id }.getAllDetectedLicenses()

    /**
     * Return all projects and packages that are likely to belong to one of the organizations of the given [names]. If
     * [omitExcluded] is set to true, excluded projects / packages are omitted from the result. Projects are converted
     * to packages in the result. If no analyzer result is present an empty set is returned.
     */
    @Suppress("UNUSED") // This is intended to be mostly used via scripting.
    fun getOrgPackages(vararg names: String, omitExcluded: Boolean = false): SortedSet<Package> {
        val vendorPackages = sortedSetOf<Package>()

        getProjects().filter {
            it.id.isFromOrg(*names) && (!omitExcluded || !isProjectExcluded(it.id))
        }.mapTo(vendorPackages) {
            it.toPackage()
        }

        getPackages().filter { (pkg, _) ->
            pkg.id.isFromOrg(*names) && (!omitExcluded || !isPackageExcluded(pkg.id))
        }.mapTo(vendorPackages) {
            it.pkg
        }

        return vendorPackages
    }

    @Suppress("UNUSED") // This is intended to be mostly used via scripting.
    fun getUncuratedPackageById(id: Identifier): Package? =
        getPackage(id)?.toUncuratedPackage()
            ?: getProject(id)?.toPackage()

    /**
     * Returns the path of the definition file of the [project], relative to the analyzer root. If the project was
     * checked out from a VCS the analyzer root is the root of the working tree, if the project was not checked out from
     * a VCS the analyzer root is the input directory of the analyzer.
     */
    fun getDefinitionFilePathRelativeToAnalyzerRoot(project: Project) =
        getFilePathRelativeToAnalyzerRoot(project, project.definitionFilePath)

    /**
     * Returns the path of a file contained in [project], relative to the analyzer root. If the project was checked out
     * from a VCS the analyzer root is the root of the working tree, if the project was not checked out from a VCS the
     * analyzer root is the input directory of the analyzer.
     */
    fun getFilePathRelativeToAnalyzerRoot(project: Project, path: String): String {
        val vcsPath = repository.getRelativePath(project.vcsProcessed)

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
     * Return true if the project or package with the given identifier is excluded.
     */
    @Suppress("UNUSED") // This is intended to be mostly used via scripting.
    fun isExcluded(id: Identifier): Boolean =
        getProject(id)?.let {
            // An excluded project could still be included as a dependency of another non-excluded project.
            isProjectExcluded(id) && isPackageExcluded(id)
        } ?: isPackageExcluded(id)

    /**
     * Return true if all occurrences of the package identified by the given [id] are excluded.
     */
    fun isPackageExcluded(id: Identifier): Boolean {
        val project = getProject(id)
        if (project != null && !isProjectExcluded(id)) {
            return false
        }

        val pkg = getPackage(id)
        if (pkg != null && !(packages[id]!!.isExcluded)) {
            return false
        }

        if (project == null && pkg == null) {
            return true
        }

        return project != null || pkg != null
    }

    /**
     * True if the given [project] is excluded.
     */
    fun isProjectExcluded(id: Identifier): Boolean = projects[id]?.isExcluded ?: false

    /**
     * Return a copy of this [OrtResult] with the [Repository.config] replaced by [config].
     */
    fun replaceConfig(config: RepositoryConfiguration): OrtResult =
        copy(repository = repository.copy(config = config)).also { it.data += data }

    /**
     * Return a copy of this [OrtResult] with the [PackageCurations]s replaced by the given [curations].
     */
    fun replacePackageCurations(curations: List<PackageCuration>): OrtResult =
        copy(
            analyzer = analyzer?.copy(
                result = analyzer.result.copy(
                    packages = getPackages().map { curatedPackage ->
                        val uncuratedPackage = CuratedPackage(curatedPackage.toUncuratedPackage(), emptyList())
                        curations
                            .filter { it.isApplicable(curatedPackage.pkg.id) }
                            .fold(uncuratedPackage) { current, packageCuration -> packageCuration.apply(current) }
                    }.toSortedSet()
                )
            )
        ).also { it.data += data }

    fun getProject(id: Identifier): Project? = projects[id]?.project

    fun getPackage(id: Identifier): CuratedPackage? = packages[id]?.curatedPackage

    @JsonIgnore
    fun getPackages(): Set<CuratedPackage> = analyzer?.result?.packages ?: emptySet()

    /**
     * Return all [Project]s contained in this [OrtResult].
     */
    @JsonIgnore
    fun getProjects(): Set<Project> = analyzer?.result?.projects.orEmpty()

    /**
     * Return all [RuleViolation]s contained in this [OrtResult].
     */
    @JsonIgnore
    fun getRuleViolations(): List<RuleViolation> = evaluator?.violations.orEmpty()

    @JsonIgnore
    fun getExcludes(): Excludes = repository.config.excludes ?: Excludes()

    /**
     * Return the [Resolution]s contained in the repository configuration of this [OrtResult].
     */
    @JsonIgnore
    fun getResolutions(): Resolutions = repository.config.resolutions.orEmpty()
}
