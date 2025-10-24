/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.DependencyGraphNavigator
import org.ossreviewtoolkit.model.DependencyNode
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.utils.DependencyHandler

class PackageManagerDependencyHandler(
    private val analyzerResult: AnalyzerResult
) : DependencyHandler<ResolvableDependencyNode> {
    private val navigator = DependencyGraphNavigator(analyzerResult.dependencyGraphs)

    override fun createPackage(dependency: ResolvableDependencyNode, issues: MutableCollection<Issue>): Package? =
        analyzerResult.packages.find { it.id == dependency.id }

    override fun dependenciesFor(dependency: ResolvableDependencyNode): List<ResolvableDependencyNode> =
        buildList {
            dependency.visitDependencies { dependencies ->
                dependencies.forEach { node ->
                    addAll(resolvePackageManagerDependency(node))
                }
            }
        }

    override fun identifierFor(dependency: ResolvableDependencyNode): Identifier = dependency.id

    override fun issuesFor(dependency: ResolvableDependencyNode): List<Issue> = dependency.issues

    override fun linkageFor(dependency: ResolvableDependencyNode): PackageLinkage = dependency.linkage

    fun resolvePackageManagerDependency(dependency: DependencyNode): List<ResolvableDependencyNode> =
        dependency.toPackageManagerDependency()?.let { packageManagerDependency ->
            packageManagerDependency.findProjects(analyzerResult).map { project ->
                val dependencies = navigator.directDependencies(project, packageManagerDependency.scope)

                ProjectScopeDependencyNode(
                    id = project.id,
                    linkage = packageManagerDependency.linkage,
                    issues = emptyList(),
                    dependencies = dependencies.map { it.getStableReference() }
                )
            }
        } ?: listOf(DependencyNodeDelegate(dependency.getStableReference()))
}
