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

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.DependencyGraph
import org.ossreviewtoolkit.model.DependencyNode
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.plugins.api.PluginConfig
import org.ossreviewtoolkit.utils.common.alsoIfNull

private const val TYPE = "PackageManagerDependency"

private fun String.encodeColon() = replace(':', '\u0000')
private fun String.decodeColon() = replace('\u0000', ':')

/**
 * Return the list of enabled [PackageManager]s based on the [AnalyzerConfiguration.enabledPackageManagers] and
 * [AnalyzerConfiguration.disabledPackageManagers] configuration properties.
 */
fun AnalyzerConfiguration.determineEnabledPackageManagers(): Set<PackageManagerFactory> {
    val enabled = enabledPackageManagers.mapNotNull { name ->
        PackageManagerFactory.ALL[name].alsoIfNull {
            logger.error {
                "Package manager '$name' is configured to be enabled but is not available in the classpath. It must " +
                    "be one of: ${PackageManagerFactory.ALL.keys.joinToString()}."
            }
        }
    }

    val disabled = disabledPackageManagers?.mapNotNull { name ->
        PackageManagerFactory.ALL[name].alsoIfNull {
            logger.warn {
                "Package manager '$name' is configured to be disabled but is not available in the classpath."
            }
        }
    }.orEmpty()

    return enabled.toSet() - disabled.toSet()
}

/**
 * Encode this dependency on another package manager into a [PackageReference] with optional [issues].
 */
fun PackageManagerDependency.toPackageReference(issues: List<Issue> = emptyList()): PackageReference =
    PackageReference(
        id = Identifier(
            type = TYPE,
            namespace = packageManager,
            name = definitionFile.encodeColon(),
            version = "$linkage@$scope"
        ),
        issues = issues
    )

/**
 * Decode this dependency node into a [PackageManagerDependency], or return null if this is not a package manager
 * dependency.
 */
internal fun DependencyNode.toPackageManagerDependency(): PackageManagerDependency? =
    id.type.takeIf { it == TYPE }?.let {
        PackageManagerDependency(
            packageManager = id.namespace,
            definitionFile = id.name.decodeColon(),
            scope = id.version.substringAfter('@'),
            linkage = PackageLinkage.valueOf(id.version.substringBefore('@'))
        )
    }

/**
 * Resolves the scopes of all [Project]s in this [OrtResult] with [Project.withResolvedScopes].
 */
fun OrtResult.withResolvedScopes(): OrtResult =
    copy(
        analyzer = analyzer?.copy(
            result = checkNotNull(analyzer).result.withResolvedScopes()
        )
    )

/**
 * Return a result, in which all contained [Project]s have their scope information resolved. If this result has shared
 * dependency graphs, the projects referring to one of these graphs are replaced by corresponding instances that store
 * their dependencies in the classic [Scope]-based format. Otherwise, this instance is returned without changes.
 */
fun AnalyzerResult.withResolvedScopes(): AnalyzerResult =
    if (dependencyGraphs.isNotEmpty()) {
        // TODO: Relax the implied assumption that there is only one enabled package manager per project type.
        val projectTypeToManagerName = PackageManagerFactory.ALL.map { (name, factory) ->
            val manager = factory.create(PluginConfig.EMPTY)
            manager.projectType to name
        }.toMap()

        copy(
            projects = projects.mapTo(mutableSetOf()) {
                val managerName = projectTypeToManagerName[it.id.type]
                    // Note: This fallback should only ever be reached from test code that has no package managers in
                    // the classpath and uses fake dependency graph map keys based on the project type.
                    ?: it.id.type

                it.withResolvedScopes(dependencyGraphs[managerName])
            },
            dependencyGraphs = emptyMap()
        )
    } else {
        this
    }

/**
 * Return a [Project] instance that has its scope information directly available, resolved from the given [graph]. This
 * function can be used to create a fully initialized [Project] if dependency information is available in a shared
 * [DependencyGraph]. In this case, the set with [Scope]s is constructed as a subset of the provided shared graph.
 * Otherwise, the result is this same object.
 */
fun Project.withResolvedScopes(graph: DependencyGraph?): Project =
    if (graph != null && scopeNames != null) {
        val qualifiedScopeNames = checkNotNull(scopeNames).mapTo(mutableSetOf()) {
            DependencyGraph.qualifyScope(id, it)
        }

        copy(
            scopeDependencies = graph.createScopes(qualifiedScopeNames),
            scopeNames = null
        )
    } else {
        this
    }
