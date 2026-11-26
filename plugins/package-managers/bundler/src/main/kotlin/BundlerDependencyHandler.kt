/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.bundler

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.createAndLogIssue
import org.ossreviewtoolkit.model.utils.DependencyGraphBuilder
import org.ossreviewtoolkit.model.utils.DependencyHandler
import org.ossreviewtoolkit.utils.common.collectMessages

/** The Gems resolved for a Bundler project, associated by their names. */
internal class BundlerProjectContext(val gemsInfo: Map<String, GemInfo>)

/**
 * A resolved [gemInfo] of the Bundler project described by [context]. Each dependency carries the context of its
 * project, as the [DependencyGraphBuilder] also compares against dependencies of previously added projects, whose Gems
 * might depend on Gems in versions that differ from those resolved for the current project.
 */
internal data class BundlerDependency(val gemInfo: GemInfo, val context: BundlerProjectContext)

internal object BundlerDependencyHandler : DependencyHandler<BundlerDependency> {
    override fun identifierFor(dependency: BundlerDependency): Identifier =
        Identifier(PACKAGE_TYPE, "", dependency.gemInfo.name, dependency.gemInfo.version)

    override fun dependenciesFor(dependency: BundlerDependency): List<BundlerDependency> =
        dependency.gemInfo.runtimeDependencies.mapNotNull { name ->
            dependency.context.gemsInfo[name]?.let { BundlerDependency(it, dependency.context) }
        }

    override fun linkageFor(dependency: BundlerDependency): PackageLinkage = PackageLinkage.DYNAMIC

    override fun issuesFor(dependency: BundlerDependency): List<Issue> =
        (dependency.gemInfo.runtimeDependencies - dependency.context.gemsInfo.keys).map {
            Issue(
                source = PROJECT_TYPE,
                message = "The dependency '$it' of Gem '${dependency.gemInfo.name}' was not resolved."
            )
        }

    override fun createPackage(dependency: BundlerDependency, issues: MutableCollection<Issue>): Package {
        val gem = dependency.gemInfo

        val gemInfo = runCatching {
            queryRubyGems(gem.name, gem.version, gem.platform)?.merge(gem)
        }.onFailure {
            issues += createAndLogIssue(
                PROJECT_TYPE,
                "Failed to query RubyGems for metadata of Gem '${gem.name}': ${it.collectMessages()}"
            )
        }.getOrNull()

        return (gemInfo ?: gem).toPackage()
    }
}
