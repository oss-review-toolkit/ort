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

import com.here.ort.model.Identifier
import com.here.ort.model.LicenseFinding
import com.here.ort.model.Package
import com.here.ort.model.PackageLinkage
import com.here.ort.model.PackageReference
import com.here.ort.model.Project
import com.here.ort.model.Scope

/**
 * A [Rule] to check a single [dependency][PackageReference].
 */
class DependencyRule(
    ruleSet: RuleSet,
    name: String,
    pkg: Package,
    detectedLicenses: List<LicenseFinding>,

    /**
     * The [dependency][PackageReference] to check.
     */
    val dependency: PackageReference,

    /**
     * The parents of this [dependency] in the dependency tree. The first entry is the root of the tree, the last entry
     * is the direct parent.
     */
    val parents: List<PackageReference>,

    /**
     * The level of this [dependency] inside the dependency tree. Starts with 1 for root level entries.
     */
    val level: Int,

    /**
     * The [Scope] that contains this [dependency].
     */
    val scope: Scope,

    /**
     * The [Project] that contains this [dependency].
     */
    val project: Project
) : PackageRule(ruleSet, name, pkg, detectedLicenses) {
    override fun describeRule() =
        "Evaluating rule '$name' for dependency '${dependency.id.toCoordinates()}' " +
                "(project=${project.id.toCoordinates()}, scope=${scope.name}, level=$level)."

    /**
     * A [RuleMatcher] that checks if the level of the [dependency] inside the dependency tree equals [level].
     */
    fun isAtTreeLevel(level: Int) =
        object : RuleMatcher {
            override fun matches() = this@DependencyRule.level == level

            override fun describe() = "isAtTreeLevel($level)"
        }

    /**
     * A [RuleMatcher] that checks if the [identifier][Project.id] of this [project] belongs to one of the provided
     * [orgs][Identifier.isFromOrg].
     */
    fun isProjectFromOrg(vararg names: String) =
        object : RuleMatcher {
            override fun matches() = project.id.isFromOrg(*names)

            override fun describe() = "isProjectFromOrg(${names.joinToString()})"
        }

    /**
     * A [RuleMatcher] that checks if the [dependency] is [statically linked][PackageLinkage].
     */
    fun isStaticallyLinked() =
        object : RuleMatcher {
            override fun matches() = dependency.linkage in listOf(PackageLinkage.STATIC, PackageLinkage.PROJECT_STATIC)

            override fun describe() = "isStaticallyLinked()"
        }
}
