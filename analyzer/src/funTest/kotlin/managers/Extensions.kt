/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.analyzer.managers

import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should

import java.io.File
import java.util.SortedSet

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerResult
import org.ossreviewtoolkit.model.DependencyGraph
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.yamlMapper

fun Any?.toYaml() = yamlMapper.writeValueAsString(this)!!

fun PackageManager.resolveSingleProject(definitionFile: File, resolveScopes: Boolean = false): ProjectAnalyzerResult {
    val managerResult = resolveDependencies(listOf(definitionFile))

    return managerResult.projectResults[definitionFile].let { resultList ->
        resultList.shouldNotBeNull()
        resultList should haveSize(1)
        val result = resultList.single()

        if (resolveScopes) managerResult.resolveScopes(result) else result
    }
}

/**
 * Transform the given [projectResult] to the classic scope-based representation of dependencies by extracting the
 * relevant information from the [DependencyGraph] stored in this [PackageManagerResult].
 */
fun PackageManagerResult.resolveScopes(projectResult: ProjectAnalyzerResult): ProjectAnalyzerResult {
    val resolvedProject = projectResult.project.withResolvedScopes(dependencyGraph)

    // The result must contain packages for all the dependencies declared by the project; otherwise, the
    // check in ProjectAnalyzerResult.init fails. When using a shared dependency graph, the set of packages
    // is typically empty, so it has to be populated manually from the subset of shared packages that are
    // referenced from this project. If there is a single project only, use all packages; this handles corner
    // cases with package managers producing packages not assigned to project scopes.
    val packages = projectResult.packages.takeUnless { it.isEmpty() }
        ?: if (projectResults.size > 1) resolvedProject.filterReferencedPackages(sharedPackages) else sharedPackages
    return projectResult.copy(project = resolvedProject, packages = packages.toSortedSet())
}

/**
 * Return only those packages from the given set of [allPackages] that are referenced by this [Project].
 */
private fun Project.filterReferencedPackages(allPackages: Set<Package>): SortedSet<Package> =
    collectDependencies().run { allPackages.filter { it.id in this }.toSortedSet() }
