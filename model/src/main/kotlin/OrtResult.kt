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

import com.fasterxml.jackson.annotation.JsonInclude

import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.spdx.SpdxExpression
import com.here.ort.utils.zipWithDefault

import java.util.SortedSet

/**
 * The common output format for the analyzer and scanner. It contains information about the scanned repository, and the
 * analyzer and scanner will add their result to it.
 */
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
        val evaluator: EvaluatorRun? = null,

        /**
         * A map that holds arbitrary data. Can be used by third-party tools to add custom data to the model.
         */
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        val data: CustomData = emptyMap()
) {
    /**
     * Return the concluded licenses associated to their package. If [omitExcluded] is set to true, excluded packages
     * are omitted from the result.
     */
    @Suppress("UNUSED") // This is intended to be mostly used via scripting.
    fun collectConcludedLicenses(omitExcluded: Boolean = false) =
            sortedMapOf<Identifier, SpdxExpression?>().also { licenses ->
                val excludes = repository.config.excludes.takeIf { omitExcluded }

                analyzer?.result?.let { result ->
                    result.packages.filter { excludes?.isPackageExcluded(it.pkg.id, result) != true }
                            .associateTo(licenses) { it.pkg.id to it.pkg.concludedLicense }
                }
            }

    /**
     * Return the declared licenses associated to their project / package. If [omitExcluded] is set to true, excluded
     * projects / packages are omitted from the result.
     */
    @Suppress("UNUSED") // This is intended to be mostly used via scripting.
    fun collectDeclaredLicenses(omitExcluded: Boolean = false) =
            sortedMapOf<String, SortedSet<Identifier>>().also { licenses ->
                val excludes = repository.config.excludes.takeIf { omitExcluded }

                analyzer?.result?.let { result ->
                    result.projects.forEach { project ->
                        if (excludes?.isProjectExcluded(project) != true) {
                            project.declaredLicenses.forEach { license ->
                                licenses.getOrPut(license) { sortedSetOf() } += project.id
                            }
                        }
                    }

                    result.packages.forEach { (pkg, _) ->
                        if (excludes?.isPackageExcluded(pkg.id, analyzer.result) != true) {
                            pkg.declaredLicenses.forEach { license ->
                                licenses.getOrPut(license) { sortedSetOf() } += pkg.id
                            }
                        }
                    }
                }
            }

    /**
     * Return the detected licenses associated to their project / package. If [omitExcluded] is set to true, excluded
     * projects / packages are omitted from the result.
     */
    @Suppress("UNUSED") // This is intended to be mostly used via scripting.
    fun collectDetectedLicenses(omitExcluded: Boolean = false) =
            sortedMapOf<String, SortedSet<Identifier>>().also { licenses ->
                // Note that we require the analyzer result here to determine whether a package has been implicitly
                // excluded via its project or scope.
                val excludes = repository.config.excludes.takeIf { omitExcluded && analyzer != null }

                scanner?.results?.scanResults?.forEach { result ->
                    // At this point we know that analyzer != null if excludes != null.
                    if (excludes?.isPackageExcluded(result.id, analyzer!!.result) != true) {
                        result.getAllDetectedLicenses().forEach { license ->
                            licenses.getOrPut(license) { sortedSetOf() } += result.id
                        }
                    }
                }
            }

    /**
     * Return the dependencies of the given [pkg], up to and including a depth of [maxDepth] where counting starts at 0
     * (for the [Package] itself) and 1 are direct dependencies etc. A value below 0 means to not limit the depth.
     */
    fun collectDependencies(pkg: Package, maxLevel: Int = -1): SortedSet<PackageReference> {
        val dependencies = sortedSetOf<PackageReference>()

        analyzer?.result?.apply {
            projects.forEach { project ->
                project.findReferences(pkg.id).forEach { ref ->
                    dependencies += ref.collectDependencies(maxLevel)
                }
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
     * Return the concluded license for the given package [id], or null if there is no concluded license.
     */
    fun getConcludedLicensesForId(id: Identifier) =
            analyzer?.result?.run {
                packages.find { it.pkg.id == id }?.pkg?.concludedLicense
            }

    /**
     * Return the declared licenses for the given [id] which may either refer to a project or to a package. If [id] is
     * not found an empty set is returned.
     */
    fun getDeclaredLicensesForId(id: Identifier) =
            analyzer?.result?.run {
                projects.find { it.id == id }?.declaredLicenses
                        ?: packages.find { it.pkg.id == id }?.pkg?.declaredLicenses
            } ?: sortedSetOf<String>()

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
        val excludes = repository.config.excludes.takeIf { omitExcluded }

        analyzer?.result?.apply {
            projects.filter {
                it.id.isFromOrg(*names) && excludes?.isProjectExcluded(it) != true
            }.mapTo(vendorPackages) {
                it.toPackage()
            }

            packages.filter { (pkg, _) ->
                pkg.id.isFromOrg(*names) && excludes?.isPackageExcluded(pkg.id, analyzer.result) != true
            }.mapTo(vendorPackages) {
                it.pkg
            }
        }

        return vendorPackages
    }

    /**
     * Return true if the project or package with the given identifier is excluded.
     */
    @Suppress("UNUSED") // This is intended to be mostly used via scripting.
    fun isExcluded(id: Identifier) =
            analyzer?.result?.let { repository.config.excludes?.isExcluded(id, it) } == true

    /**
     * Return a copy of this [OrtResult] with the [Repository.config] replaced by [config].
     */
    fun replaceConfig(config: RepositoryConfiguration) = copy(repository = repository.copy(config = config))
}
