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
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.utils.DependencyHandler

private const val TYPE = "PackageManagerDependency"

class PackageManagerDependencyHandler(
    private val analyzerResult: AnalyzerResult
) : DependencyHandler<ResolvableDependencyNode> {
    companion object {
        /**
         * Create a [PackageReference] that points to the result of a [scope] in the [definitionFile] from another
         * package manager with the provided [linkage]. The analyzer will replace this reference with the dependency
         * tree of the provided scope after all package managers have finished.
         */
        fun createPackageManagerDependency(
            packageManager: String,
            definitionFile: String,
            scope: String,
            linkage: PackageLinkage,
            issues: List<Issue> = emptyList()
        ): PackageReference =
            PackageReference(
                id = Identifier(
                    type = TYPE,
                    namespace = packageManager,
                    name = definitionFile.encodeColon(),
                    version = "$linkage@$scope"
                ),
                issues = issues
            )

        private fun getPackageManagerDependency(node: DependencyNode): PackageManagerDependency? =
            node.id.type.takeIf { it == TYPE }?.let {
                PackageManagerDependency(
                    packageManager = node.id.namespace,
                    definitionFile = node.id.name.decodeColon(),
                    scope = node.id.version.substringAfter('@'),
                    linkage = PackageLinkage.valueOf(node.id.version.substringBefore('@'))
                )
            }
    }

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

    override fun issuesForDependency(dependency: ResolvableDependencyNode): List<Issue> = dependency.issues

    override fun linkageFor(dependency: ResolvableDependencyNode): PackageLinkage = dependency.linkage

    fun resolvePackageManagerDependency(dependency: DependencyNode): List<ResolvableDependencyNode> =
        getPackageManagerDependency(dependency)?.let { packageManagerDependency ->
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
