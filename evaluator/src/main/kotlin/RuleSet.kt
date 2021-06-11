/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.RuleViolation
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.licenses.LicenseInfoResolver
import org.ossreviewtoolkit.model.utils.createLicenseInfoResolver
import org.ossreviewtoolkit.utils.log

/**
 * A set of evaluator [Rule]s, using an [ortResult] as input.
 */
class RuleSet(
    val ortResult: OrtResult,
    val licenseInfoResolver: LicenseInfoResolver = ortResult.createLicenseInfoResolver()
) {
    /**
     * The list of all issues created by the rules of this [RuleSet].
     */
    val violations = mutableSetOf<RuleViolation>()

    /**
     * A DSL function to configure a [PackageRule]. The rule is applied to each [Package] and [Project] contained in
     * [ortResult].
     */
    fun packageRule(name: String, configure: PackageRule.() -> Unit) {
        val packages = ortResult.analyzer?.result?.let { analyzerResult ->
            analyzerResult.projects.map { it.toPackage().toCuratedPackage() } + analyzerResult.packages
        }.orEmpty()

        packages.forEach { curatedPackage ->
            val resolvedLicenseInfo = licenseInfoResolver.resolveLicenseInfo(curatedPackage.pkg.id)
            PackageRule(this, name, curatedPackage.pkg, curatedPackage.curations, resolvedLicenseInfo).apply {
                configure()
                evaluate()
            }
        }
    }

    /**
     * A DSL function to configure a [DependencyRule]. The rule is applied to each [PackageReference] from the
     * dependency trees contained in [ortResult]. If the same dependency appears multiple times in the dependency tree,
     * the rule will be applied on each occurrence.
     */
    fun dependencyRule(name: String, configure: DependencyRule.() -> Unit) {
        fun traverse(
            pkgRef: PackageReference,
            ancestors: List<PackageReference>,
            level: Int,
            scope: Scope,
            project: Project,
            visitedPackages: MutableSet<PackageReference>
        ) {
            if (visitedPackages.contains(pkgRef)) {
                log.debug { "Skipping rule $name for already visited dependency ${pkgRef.id.toCoordinates()}." }
                return
            }

            visitedPackages += pkgRef

            val curatedPackage = ortResult.getPackage(pkgRef.id)
                ?: ortResult.getProject(pkgRef.id)?.toPackage()?.toCuratedPackage()

            if (curatedPackage == null) {
                log.warn { "Could not find package for dependency ${pkgRef.id.toCoordinates()}, skipping rule $name." }
            } else {
                val resolvedLicenseInfo = licenseInfoResolver.resolveLicenseInfo(curatedPackage.pkg.id)

                DependencyRule(
                    this,
                    name,
                    curatedPackage.pkg,
                    curatedPackage.curations,
                    resolvedLicenseInfo,
                    pkgRef,
                    ancestors,
                    level,
                    scope,
                    project
                ).apply {
                    configure()
                    evaluate()
                }
            }

            pkgRef.dependencies.forEach { dependency ->
                traverse(dependency, listOf(pkgRef) + ancestors, level + 1, scope, project, visitedPackages)
            }
        }

        ortResult.analyzer?.result?.projects?.forEach { project ->
            project.scopes.forEach { scope ->
                val visitedPackages = mutableSetOf<PackageReference>()
                scope.dependencies.forEach { pkgRef ->
                    traverse(pkgRef, emptyList(), 0, scope, project, visitedPackages)
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
    configure: RuleSet.() -> Unit
) = RuleSet(ortResult, licenseInfoResolver).apply(configure)
