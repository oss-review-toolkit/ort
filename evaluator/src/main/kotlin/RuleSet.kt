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

package com.here.ort.evaluator

import com.here.ort.model.OrtIssue
import com.here.ort.model.OrtResult
import com.here.ort.model.Package
import com.here.ort.model.PackageReference
import com.here.ort.model.Project
import com.here.ort.model.Scope

/**
 * A set of evaluator [Rule]s, using an [ortResult] as input.
 */
class RuleSet(val ortResult: OrtResult) {
    /**
     * The list of all issues created by the rules of this [RuleSet].
     */
    val issues = mutableSetOf<OrtIssue>()

    /**
     * A DSL function to configure a [PackageRule]. The rule is applied to each [Package] and [Project] contained in
     * [ortResult].
     */
    fun packageRule(name: String, configure: PackageRule.() -> Unit) {
        val packages = ortResult.analyzer?.result?.let { analyzerResult ->
            analyzerResult.projects.map { it.toPackage() } + analyzerResult.packages.map { it.pkg }
        }.orEmpty()

        packages.forEach { pkg ->
            val licenseFindings = ortResult.collectLicenseFindings()[pkg.id].orEmpty().keys.toList()
            PackageRule(this, name, pkg, licenseFindings).apply {
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
            project: Project
        ) {
            val pkg = ortResult.analyzer?.result?.packages?.find { it.pkg.id == pkgRef.id }?.pkg!!
            val detectedLicenses = ortResult.collectLicenseFindings()[pkg.id].orEmpty().keys.toList()

            DependencyRule(this, name, pkg, detectedLicenses, pkgRef, ancestors, level, scope, project).apply {
                configure()
                evaluate()
            }

            pkgRef.dependencies.forEach { dependency ->
                traverse(dependency, listOf(pkgRef) + ancestors, level + 1, scope, project)
            }
        }

        ortResult.analyzer?.result?.projects?.forEach { project ->
            project.scopes.forEach { scope ->
                scope.dependencies.forEach { pkgRef ->
                    traverse(pkgRef, emptyList(), 0, scope, project)
                }
            }
        }
    }
}

/**
 * A DSL function to configure a [RuleSet].
 */
fun ruleSet(ortResult: OrtResult, configure: RuleSet.() -> Unit) = RuleSet(ortResult).apply(configure)
