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

import org.ossreviewtoolkit.model.DependencyNode
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.utils.common.alsoIfNull

private const val TYPE = "PackageManagerDependency"

private fun String.encodeColon() = replace(':', '\u0000')
private fun String.decodeColon() = replace('\u0000', ':')

/**
 * Return the list of enabled [PackageManager]s based on the [AnalyzerConfiguration.enabledPackageManagers] and
 * [AnalyzerConfiguration.disabledPackageManagers] configuration properties and the
 * [default][PackageManagerFactory.isEnabledByDefault] of the [PackageManager]s.
 */
fun AnalyzerConfiguration.determineEnabledPackageManagers(): Set<PackageManagerFactory> {
    val enabled = enabledPackageManagers?.mapNotNull { name ->
        PackageManagerFactory.ALL[name].alsoIfNull {
            logger.error {
                "Package manager '$name' is configured to be enabled but is not available in the classpath. It must " +
                    "be one of: ${PackageManagerFactory.ALL.keys.joinToString()}."
            }
        }
    } ?: PackageManagerFactory.ENABLED_BY_DEFAULT

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
