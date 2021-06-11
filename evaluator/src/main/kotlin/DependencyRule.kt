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

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageCurationResult
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.licenses.ResolvedLicenseInfo
import org.ossreviewtoolkit.spdx.enumSetOf

/**
 * A [Rule] to check a single [dependency][PackageReference].
 */
class DependencyRule(
    ruleSet: RuleSet,
    name: String,
    pkg: Package,
    curations: List<PackageCurationResult>,
    resolvedLicenseInfo: ResolvedLicenseInfo,

    /**
     * The [dependency][PackageReference] to check.
     */
    val dependency: PackageReference,

    /**
     * The ancestors of the [dependency] in the dependency tree. The first entry is the root of the tree, the last entry
     * (at [level] - 1) is the direct parent.
     */
    val ancestors: List<PackageReference>,

    /**
     * The level of the [dependency] inside the dependency tree. Starts with 0 for root level entries.
     */
    val level: Int,

    /**
     * The [Scope] that contains the [dependency].
     */
    val scope: Scope,

    /**
     * The [Project] that contains the [dependency].
     */
    val project: Project
) : PackageRule(ruleSet, name, pkg, curations, resolvedLicenseInfo) {
    override val description =
        "Evaluating rule '$name' for dependency '${dependency.id.toCoordinates()}' " +
                "(project=${project.id.toCoordinates()}, scope=${scope.name}, level=$level)."

    override fun issueSource() =
        "$name - ${pkg.id.toCoordinates()} (dependency of ${project.id.toCoordinates()} in scope ${scope.name})"

    /**
     * A [RuleMatcher] that checks if the level of the [dependency] inside the dependency tree equals [level].
     */
    fun isAtTreeLevel(level: Int) =
        object : RuleMatcher {
            override val description = "isAtTreeLevel($level)"

            override fun matches() = this@DependencyRule.level == level
        }

    /**
     * A [RuleMatcher] that checks if the [identifier][Project.id] of the [project] belongs to one of the provided
     * [orgs][Identifier.isFromOrg].
     */
    fun isProjectFromOrg(vararg names: String) =
        object : RuleMatcher {
            override val description = "isProjectFromOrg(${names.joinToString()})"

            override fun matches() = project.id.isFromOrg(*names)
        }

    /**
     * A [RuleMatcher] that checks if the [dependency] is [statically linked][PackageLinkage].
     */
    fun isStaticallyLinked() =
        object : RuleMatcher {
            override val description = "isStaticallyLinked()"

            override fun matches() =
                dependency.linkage in enumSetOf(PackageLinkage.STATIC, PackageLinkage.PROJECT_STATIC)
        }
}
