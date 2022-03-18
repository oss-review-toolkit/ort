/*
 * Copyright (C) 2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.reporter.reporters.gitlab

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.reporter.reporters.gitlab.GitLabLicenseModel.Dependency
import org.ossreviewtoolkit.reporter.reporters.gitlab.GitLabLicenseModel.License
import org.ossreviewtoolkit.utils.core.log
import org.ossreviewtoolkit.utils.spdx.SpdxLicense
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseIdExpression
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseWithExceptionExpression
import org.ossreviewtoolkit.utils.spdx.SpdxSingleLicenseExpression

/**
 * Maps an [OrtResult] to a [GitLabLicenseModel].
 */
internal object GitLabLicenseModelMapper {
    fun map(ortResult: OrtResult, skipExcluded: Boolean): GitLabLicenseModel {
        val packagesWithDefinitionFilePaths = ortResult.getTargetPackagesWithDefinitionFiles(skipExcluded)

        return GitLabLicenseModel(
            licenses = packagesWithDefinitionFilePaths.keys.toLicenses(),
            dependencies = packagesWithDefinitionFilePaths.toDependencies()
        )
    }
}

private fun Collection<Package>.toLicenses(): List<License> {
    val decomposedLicenses = flatMapTo(mutableSetOf()) { pkg ->
        pkg.declaredLicensesProcessed.spdxExpression?.decompose().orEmpty()
    }

    return decomposedLicenses.map { singleLicenseExpression ->
        License(
            id = singleLicenseExpression.simpleLicense(),
            name = singleLicenseExpression.toLicenseName(),
            url = singleLicenseExpression.getLicenseUrl().orEmpty()
        )
    }.sortedBy { it.id }
}

private fun Map<Package, List<String>>.toDependencies(): List<Dependency> =
    map { (pkg, definitionFilePaths) ->
        pkg.toDependency(definitionFilePaths)
    }.sortedBy { "${it.packageManager}${it.name}${it.version}" }

private fun OrtResult.getTargetPackagesWithDefinitionFiles(skipExcluded: Boolean): Map<Package, List<String>> {
    val result = mutableMapOf<Identifier, MutableList<String>>()

    val packages = getPackages().associate { it.pkg.id to it.pkg }

    getProjects(omitExcluded = skipExcluded).forEach { project ->
        val definitionFilePath = project.definitionFilePath

        // Omit non-referenced and maybe also excluded packages:
        dependencyNavigator.projectDependencies(project) { !skipExcluded || !isExcluded(it.id) }.forEach { id ->
            result.getOrPut(id) { mutableListOf() } += definitionFilePath
        }
    }

    return result.filter { it.key in packages.keys }.mapKeys { packages.getValue(it.key) }
}

private fun Package.toDependency(definitionFilePaths: Collection<String>): Dependency =
    Dependency(
        name = id.name,
        version = id.version,
        licenses = declaredLicensesProcessed.spdxExpression?.decompose().orEmpty().map { it.toString() },
        path = definitionFilePaths.sorted().joinToString(","),
        packageManager = id.toPackageManagerName()
    )

private fun SpdxSingleLicenseExpression.toLicenseName(): String {
    // TODO: Get the full name also for non-SPDX / ScanCode licenses.
    val spdxLicenseId = when (this) {
        is SpdxLicenseWithExceptionExpression -> license.simpleLicense()
        is SpdxLicenseIdExpression -> this.simpleLicense()
        else -> ""
    }

    return SpdxLicense.forId(spdxLicenseId)?.fullName.orEmpty()
}

/**
 * Map the [Identifier.type] to the GitLab license model package names. The mapping is based on the examples found
 * under https://gitlab.com/gitlab-org/security-products/license-management/-/tree/f4ec1f1bf826654ab963d32a2d4a2588ecb91c04/spec/fixtures/expected.
 */
private fun Identifier.toPackageManagerName(): String =
    when (type) {
        "Bower" -> "bower"
        "Bundler" -> "bundler"
        "Composer" -> "composer"
        "Conan" -> "conan"
        "GoMod" -> "go"
        "GoDep" -> "go" // This mapping is a guess as it is not illustrated by the specification and the examples.
        "Gradle" -> "gradle"
        "Maven" -> "maven"
        "NPM" -> "npm"
        "NuGet" -> "nuget"
        "PIP" -> "pip"
        "Yarn" -> "yarn"
        else -> type.lowercase().also {
            log.info { "No mapping defined for package manager '$type', guessing '$it'." }
        }
    }
