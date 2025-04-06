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

package org.ossreviewtoolkit.evaluator

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.model.DependencyNode
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.RuleViolation
import org.ossreviewtoolkit.model.licenses.LicenseInfoResolver
import org.ossreviewtoolkit.model.utils.DefaultResolutionProvider
import org.ossreviewtoolkit.model.utils.ResolutionProvider
import org.ossreviewtoolkit.model.utils.createLicenseInfoResolver

/**
 * A set of evaluator [Rule]s, using an [ortResult] as input.
 */
class RuleSet(
    val ortResult: OrtResult,
    val licenseInfoResolver: LicenseInfoResolver,
    val resolutionProvider: ResolutionProvider,
    val projectSourceResolver: SourceTreeResolver
) {
    /**
     * The set of all issues created by the rules of this [RuleSet].
     */
    val violations = mutableSetOf<RuleViolation>()

    /**
     * A DSL function to configure an [OrtResultRule]. The rule is applied once to [ortResult].
     */
    fun ortResultRule(name: String, configure: OrtResultRule.() -> Unit) {
        OrtResultRule(this, name).apply {
            configure()
            evaluate()
        }
    }

    /**
     * A DSL function to configure an [ProjectSourceRule]. The rule is applied once to [ortResult].
     */
    fun projectSourceRule(name: String, configure: ProjectSourceRule.() -> Unit) {
        ProjectSourceRule(this, name).apply {
            configure()
            evaluate()
        }
    }

    /**
     * A DSL function to configure a [PackageRule]. The rule is applied to each [Package] and [Project] contained in
     * [ortResult].
     */
    fun packageRule(name: String, configure: PackageRule.() -> Unit) {
        val packages = ortResult.getProjects().map { it.toPackage().toCuratedPackage() } + ortResult.getPackages()

        packages.forEach { curatedPackage ->
            val resolvedLicenseInfo = licenseInfoResolver.resolveLicenseInfo(curatedPackage.metadata.id)
            PackageRule(this, name, curatedPackage, resolvedLicenseInfo).apply {
                configure()
                evaluate()
            }
        }
    }

    /**
     * A DSL function to configure a [DependencyRule]. The rule is applied to each [DependencyNode] from the
     * dependency trees contained in [ortResult]. If the same dependency appears multiple times in the same scope, the
     * rule will be applied only once.
     */
    fun dependencyRule(name: String, configure: DependencyRule.() -> Unit) {
        fun traverse(
            node: DependencyNode,
            ancestors: List<DependencyNode>,
            level: Int,
            scopeName: String,
            project: Project,
            visitedPackages: MutableSet<DependencyNode>
        ) {
            if (node in visitedPackages) {
                logger.debug { "Skipping rule $name for already visited dependency ${node.id.toCoordinates()}." }
                return
            }

            visitedPackages += node

            val curatedPackage = ortResult.getPackage(node.id)
                ?: ortResult.getProject(node.id)?.toPackage()?.toCuratedPackage()

            if (curatedPackage == null) {
                logger.warn { "Could not find package for dependency ${node.id.toCoordinates()}, skipping rule $name." }
            } else {
                val resolvedLicenseInfo = licenseInfoResolver.resolveLicenseInfo(curatedPackage.metadata.id)

                DependencyRule(
                    this,
                    name,
                    curatedPackage,
                    resolvedLicenseInfo,
                    node,
                    ancestors,
                    level,
                    scopeName,
                    project
                ).apply {
                    configure()
                    evaluate()
                }
            }

            node.visitDependencies { dependencies ->
                dependencies.forEach { dependency ->
                    traverse(
                        dependency,
                        listOf(node.getStableReference()) + ancestors,
                        level + 1,
                        scopeName,
                        project,
                        visitedPackages
                    )
                }
            }
        }

        ortResult.getProjects().forEach { project ->
            ortResult.dependencyNavigator.scopeNames(project).forEach { scopeName ->
                val visitedPackages = mutableSetOf<DependencyNode>()
                ortResult.dependencyNavigator.directDependencies(project, scopeName).forEach { dependency ->
                    traverse(dependency, emptyList(), 0, scopeName, project, visitedPackages)
                }
            }
        }
    }
}

/**
 * A DSL function to configure a [RuleSet].
 */
fun ruleSet(
    ortResult: OrtResult,
    licenseInfoResolver: LicenseInfoResolver = ortResult.createLicenseInfoResolver(),
    resolutionProvider: ResolutionProvider = DefaultResolutionProvider.create(),
    projectSourceResolver: SourceTreeResolver = SourceTreeResolver.forRemoteRepository(
        ortResult.repository.vcsProcessed
    ),
    configure: RuleSet.() -> Unit = {}
) = RuleSet(ortResult, licenseInfoResolver, resolutionProvider, projectSourceResolver).apply(configure)
