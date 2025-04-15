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

package org.ossreviewtoolkit.analyzer

import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.nulls.shouldNotBeNull

import java.io.File
import java.time.Instant

import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.DependencyGraph
import org.ossreviewtoolkit.model.DependencyTreeNavigator
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.PackageManagerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.ScopeExclude
import org.ossreviewtoolkit.model.config.ScopeExcludeReason
import org.ossreviewtoolkit.plugins.api.PluginConfig
import org.ossreviewtoolkit.utils.test.USER_DIR

fun PackageManager.resolveSingleProject(
    definitionFile: File,
    excludedScopes: Collection<String> = emptySet(),
    allowDynamicVersions: Boolean = false,
    resolveScopes: Boolean = false
): ProjectAnalyzerResult {
    val definitionFiles = listOf(definitionFile)
    val analyzerConfig = AnalyzerConfiguration(allowDynamicVersions = allowDynamicVersions)

    beforeResolution(USER_DIR, definitionFiles, analyzerConfig)

    val excludes = Excludes(scopes = excludedScopes.map { ScopeExclude(it, ScopeExcludeReason.TEST_DEPENDENCY_OF) })
    val managerResult = resolveDependencies(USER_DIR, definitionFiles, excludes, analyzerConfig, emptyMap())

    afterResolution(USER_DIR, definitionFiles)

    val resultList = managerResult.projectResults[definitionFile]
    resultList.shouldNotBeNull()
    resultList.shouldBeSingleton()

    val result = resultList.single().let {
        if (resolveScopes) managerResult.resolveScopes(it) else it
    }

    return result
}

/**
 * Transform the given [projectResult] to the classic scope-based representation of dependencies by extracting the
 * relevant information from the [DependencyGraph] stored in this [PackageManagerResult].
 */
fun PackageManagerResult.resolveScopes(projectResult: ProjectAnalyzerResult): ProjectAnalyzerResult {
    val resolvedProject = projectResult.project.withResolvedScopes(dependencyGraph)

    // When using a shared dependency graph, the set of packages is typically empty, so it has to be populated manually
    // from the subset of shared packages that are referenced from this project. If there is a single project only, use
    // all packages; this handles corner cases with package managers producing packages not assigned to project scopes.
    val packages = projectResult.packages.takeUnless { it.isEmpty() }
        ?: if (projectResults.size > 1) resolvedProject.filterReferencedPackages(sharedPackages) else sharedPackages
    return projectResult.copy(project = resolvedProject, packages = packages)
}

/**
 * Return only those packages from the given set of [allPackages] that are referenced by this [Project].
 * NOTE: The project is known to use the scopes structure for storing its dependencies; therefore, a
 * [DependencyTreeNavigator] can be used to access this information.
 */
private fun Project.filterReferencedPackages(allPackages: Set<Package>): Set<Package> {
    val projectDependencies = DependencyTreeNavigator.projectDependencies(this)
    return allPackages.filterTo(mutableSetOf()) { it.id in projectDependencies }
}

fun Collection<PackageReference>.withInvariantIssues(): Set<PackageReference> =
    mapTo(mutableSetOf()) { ref ->
        ref.copy(
            dependencies = ref.dependencies.withInvariantIssues(),
            issues = ref.issues.map { it.copy(timestamp = Instant.EPOCH) }
        )
    }

fun ProjectAnalyzerResult.withInvariantIssues() =
    copy(
        project = project.copy(
            scopeDependencies = project.scopeDependencies?.mapTo(mutableSetOf()) { scope ->
                scope.copy(dependencies = scope.dependencies.withInvariantIssues())
            }
        ),
        issues = issues.sortedBy { it.message }.map { it.copy(timestamp = Instant.EPOCH) }
    )

fun analyze(
    projectDir: File,
    allowDynamicVersions: Boolean = false,
    excludedScopes: Collection<String> = emptySet(),
    skipExcluded: Boolean = false,
    packageManagers: Collection<PackageManagerFactory> = PackageManagerFactory.ALL.values,
    packageManagerConfiguration: Map<String, PackageManagerConfiguration>? = null
): OrtResult {
    val config = AnalyzerConfiguration(
        allowDynamicVersions,
        enabledPackageManagers = packageManagers.map { it.descriptor.id },
        packageManagers = packageManagerConfiguration,
        skipExcluded = skipExcluded
    )

    val repositoryConfig = RepositoryConfiguration(
        excludes = Excludes(
            scopes = excludedScopes.map {
                ScopeExclude(it, ScopeExcludeReason.TEST_DEPENDENCY_OF)
            }
        )
    )

    val analyzer = Analyzer(config)
    val managedFiles = analyzer.findManagedFiles(projectDir, packageManagers, repositoryConfig)

    return analyzer.analyze(managedFiles).withResolvedScopes()
}

fun OrtResult.getAnalyzerResult(): AnalyzerResult = checkNotNull(analyzer).result

fun create(managerName: String, pluginConfig: PluginConfig) =
    PackageManagerFactory.ALL.getValue(managerName).create(pluginConfig)
