/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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

import com.here.ort.model.config.ErrorExclude
import com.here.ort.model.config.Excludes
import com.here.ort.model.config.PackageExclude
import com.here.ort.model.config.ProjectExclude
import com.here.ort.model.config.ScopeExclude

import java.util.SortedSet

abstract class ExcludesProcessor(val excludes: Excludes) : AnalyzerResultPostProcessor {
    override fun postProcess(analyzerResult: AnalyzerResult): AnalyzerResult {
        val projects = analyzerResult.projects.map { project ->
            val excludedScopes = excludes.scopes.toMutableList()
            val excludedPackages = excludes.packages.toMutableList()
            val excludedErrors = excludes.errors.toMutableList()

            val projectExclude = excludes.projects.find { it.path == project?.definitionFilePath }

            projectExclude?.let {
                excludedScopes += it.scopes
                excludedPackages += it.packages
                excludedErrors += it.errors
            }

            processProject(project, projectExclude, excludedScopes, excludedPackages, excludedErrors)
        }.toSortedSet()

        val errors = analyzerResult.errors.mapValues { (id, errors) ->
            val excludedErrors = excludes.errors.toMutableList()

            val project = analyzerResult.projects.find { it.id == id }
            excludes.projects.find { it.path == project?.definitionFilePath }?.let { projectExclude ->
                excludedErrors += projectExclude.errors
            }

            processErrors(errors, excludedErrors)
        }.filter { (_, errors) ->
            errors.isNotEmpty()
        }.toSortedMap()

        val packages = processPackages(projects, analyzerResult.packages)

        return analyzerResult.copy(projects = projects, packages = packages, errors = errors)
    }

    protected abstract fun processProject(
            project: Project,
            projectExclude: ProjectExclude?,
            excludedScopes: MutableList<ScopeExclude>,
            excludedPackages: MutableList<PackageExclude>,
            excludedErrors: MutableList<ErrorExclude>
    ): Project

    protected abstract fun processErrors(errors: List<Error>, excludedErrors: List<ErrorExclude>): List<Error>

    protected abstract fun processPackages(projects: SortedSet<Project>, packages: SortedSet<CuratedPackage>)
            : SortedSet<CuratedPackage>
}

class ExcludesMarker(excludes: Excludes) : ExcludesProcessor(excludes) {
    override fun processProject(
            project: Project,
            projectExclude: ProjectExclude?,
            excludedScopes: MutableList<ScopeExclude>,
            excludedPackages: MutableList<PackageExclude>,
            excludedErrors: MutableList<ErrorExclude>
    ): Project {
        val scopes = project.scopes.map { scope ->
            val dependencies = scope.dependencies.map { dependency ->
                dependency.traverse { pkgRef ->
                    val errors = pkgRef.errors.map { error ->
                        val excluded = excludedErrors.any { errorExclude ->
                            errorExclude.regex.matches(error.message)
                        }

                        error.copy(excluded = excluded)
                    }

                    val excluded = excludedPackages.any { it.id == pkgRef.id }

                    pkgRef.copy(errors = errors, excluded = excluded)
                }
            }.toSortedSet()

            val excluded = excludedScopes.any { it.name == scope.name }

            scope.copy(dependencies = dependencies, excluded = excluded)
        }.toSortedSet()

        val excluded = projectExclude?.let { it.exclude } ?: false

        return project.copy(scopes = scopes, excluded = excluded)
    }

    override fun processErrors(errors: List<Error>, excludedErrors: List<ErrorExclude>) =
            errors.map { error ->
                val excluded = excludedErrors.any { it.regex.matches(error.message) }

                error.copy(excluded = excluded)
            }

    override fun processPackages(projects: SortedSet<Project>, packages: SortedSet<CuratedPackage>) = packages
}

class ExcludesRemover(excludes: Excludes) : ExcludesProcessor(excludes) {
    override fun processProject(
            project: Project,
            projectExclude: ProjectExclude?,
            excludedScopes: MutableList<ScopeExclude>,
            excludedPackages: MutableList<PackageExclude>,
            excludedErrors: MutableList<ErrorExclude>
    ): Project {
        val scopes = project.scopes.filter { scope ->
            excludedScopes.none { it.name == scope.name }
        }.map { scope ->
            val dependencies = scope.dependencies.map { dependency ->
                dependency.traverse { pkgRef ->
                    val errors = pkgRef.errors.filter { error ->
                        excludedErrors.none { it.regex.matches(error.message) }
                    }

                    val excluded = excludedPackages.any { it.id == pkgRef.id }

                    pkgRef.copy(errors = errors, excluded = excluded)
                }
            }.toSortedSet()

            scope.copy(dependencies = dependencies)
        }.toSortedSet()

        return project.copy(scopes = scopes)
    }

    override fun processErrors(errors: List<Error>, excludedErrors: List<ErrorExclude>) =
            errors.filter { error ->
                excludedErrors.none { it.regex.matches(error.message) }
            }

    override fun processPackages(projects: SortedSet<Project>, packages: SortedSet<CuratedPackage>)
            : SortedSet<CuratedPackage> {
        val packageIdentifiers = projects.flatMap { project ->
            project.scopes.flatMap { it.collectDependencyIds(includeExcluded = false) }
        }.toSet()
        return packages.filter { it.pkg.id in packageIdentifiers }.toSortedSet()
    }
}
