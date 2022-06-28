/*
 * Copyright (C) 2022 Bosch.IO GmbH
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

package org.ossreviewtoolkit.analyzer.managers.utils

import com.fasterxml.jackson.module.kotlin.readValue

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.DependencyGraphNavigator
import org.ossreviewtoolkit.model.DependencyNode
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtIssue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.model.utils.DependencyHandler
import org.ossreviewtoolkit.utils.ort.log

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
            linkage: PackageLinkage
        ): PackageReference =
            PackageReference(
                id = Identifier(
                    type = TYPE,
                    namespace = "",
                    name = jsonMapper.writeValueAsString(
                        PackageManagerDependency(
                            packageManager,
                            definitionFile,
                            scope,
                            linkage
                        )
                    ).encode(),
                    version = ""
                )
            )

        private fun getPackageManagerDependency(node: DependencyNode): PackageManagerDependency? =
            when (node.id.type) {
                TYPE -> jsonMapper.readValue<PackageManagerDependency>(node.id.name.decode())
                else -> null
            }
    }

    private val navigator = DependencyGraphNavigator(analyzerResult.dependencyGraphs)

    override fun createPackage(dependency: ResolvableDependencyNode, issues: MutableList<OrtIssue>): Package? =
        analyzerResult.packages.find { it.pkg.id == dependency.id }?.pkg

    override fun dependenciesFor(dependency: ResolvableDependencyNode): Collection<ResolvableDependencyNode> =
        buildList {
            dependency.visitDependencies { dependencies ->
                dependencies.forEach { node ->
                    addAll(resolvePackageManagerDependency(node))
                }
            }
        }

    override fun identifierFor(dependency: ResolvableDependencyNode): Identifier = dependency.id

    override fun issuesForDependency(dependency: ResolvableDependencyNode): Collection<OrtIssue> = dependency.issues

    override fun linkageFor(dependency: ResolvableDependencyNode): PackageLinkage = dependency.linkage

    fun resolvePackageManagerDependency(dependency: DependencyNode): List<ResolvableDependencyNode> =
        getPackageManagerDependency(dependency)?.let { packageManagerDependency ->
            packageManagerDependency.findProjects(analyzerResult).map { project ->
                val dependencies = navigator.directDependencies(project, packageManagerDependency.scope)

                ProjectScopeDependencyNode(
                    id = project.id,
                    linkage = packageManagerDependency.linkage,
                    issues = emptyList(),
                    dependencies = dependencies.map { it.getStableReference() }.toList().asSequence()
                )
            }
        } ?: listOf(DependencyNodeDelegate(dependency.getStableReference()))
}

private data class PackageManagerDependency(
    val packageManager: String,
    val definitionFile: String,
    val scope: String,
    val linkage: PackageLinkage
) {
    fun findProjects(analyzerResult: AnalyzerResult): List<Project> =
        analyzerResult.projects.filter { it.definitionFilePath == definitionFile }.also { projects ->
            if (projects.isEmpty()) {
                log.warn { "Could not find any project for definition file '$definitionFile'." }
            }

            projects.forEach { verify(it) }
        }

    @ExperimentalContracts
    fun verify(project: Project?) {
        contract {
            returns() implies (project != null)
        }

        requireNotNull(project) {
            "Could not find a project for the definition file '$definitionFile'."
        }

        require(project.id.type == packageManager) {
            "The project '${project.id.toCoordinates()}' from definition file '$definitionFile' uses the wrong " +
                    "package manager '${project.id.type}', expected is '$packageManager'."
        }

        requireNotNull(project.scopeNames) {
            "The project '${project.id.toCoordinates()}' from definition file '$definitionFile' does not use a " +
                    "dependency graph."
        }

        if (scope !in project.scopeNames.orEmpty()) {
            log.warn {
                "The project '${project.id.toCoordinates()}' from definition file '$definitionFile' does not contain " +
                        "the requested scope '$scope'."
            }
        }
    }
}

sealed class ResolvableDependencyNode : DependencyNode

class ProjectScopeDependencyNode(
    override val id: Identifier,
    override val linkage: PackageLinkage,
    override val issues: List<OrtIssue>,
    private val dependencies: Sequence<DependencyNode>
) : ResolvableDependencyNode() {
    override fun <T> visitDependencies(block: (Sequence<DependencyNode>) -> T): T = block(dependencies)
}

class DependencyNodeDelegate(private val node: DependencyNode) : ResolvableDependencyNode() {
    override val id: Identifier = node.id
    override val linkage = node.linkage
    override val issues = node.issues
    override fun <T> visitDependencies(block: (Sequence<DependencyNode>) -> T): T = node.visitDependencies(block)
}

private fun String.encode() = replace(":", "__")
private fun String.decode() = replace("__", ":")
