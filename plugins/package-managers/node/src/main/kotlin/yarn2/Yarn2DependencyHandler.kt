/*
 * Copyright (C) 2022 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.node.yarn2

import java.io.File

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.utils.DependencyHandler
import org.ossreviewtoolkit.plugins.packagemanagers.node.ModuleInfoResolver
import org.ossreviewtoolkit.plugins.packagemanagers.node.NodePackageManagerType
import org.ossreviewtoolkit.plugins.packagemanagers.node.PackageJson
import org.ossreviewtoolkit.plugins.packagemanagers.node.parsePackage
import org.ossreviewtoolkit.utils.common.withoutPrefix

import org.semver4j.Semver
import org.semver4j.range.RangeListFactory

internal class Yarn2DependencyHandler(
    private val moduleInfoResolver: ModuleInfoResolver
) : DependencyHandler<PackageInfo> {
    private lateinit var workingDir: File
    private val packageJsonForModuleId = mutableMapOf<String, PackageJson>()
    private val packageInfoForLocator = mutableMapOf<String, PackageInfo>()

    /**
     * A cache for the results of [resolvePackageInfos], as the same dependency is resolved repeatedly while the
     * dependency graph is built, and the fallback resolution is expensive.
     */
    private val packageInfosForDependency = mutableMapOf<PackageInfo.Dependency, List<PackageInfo>>()

    fun setContext(
        workingDir: File,
        packageJsonForModuleId: Map<String, PackageJson>,
        packageInfoForLocator: Map<String, PackageInfo>
    ) {
        this.workingDir = workingDir

        this.packageInfoForLocator.apply {
            clear()
            putAll(packageInfoForLocator)
        }

        this.packageJsonForModuleId.apply {
            clear()
            putAll(packageJsonForModuleId)
        }

        // Cached resolutions are not reusable across definition files, as they are resolved against
        // `packageInfoForLocator`, whose content is replaced here.
        packageInfosForDependency.clear()
    }

    override fun identifierFor(dependency: PackageInfo): Identifier =
        Identifier(
            type = with(NodePackageManagerType.YARN2) { if (dependency.isProject) projectType else packageType },
            namespace = dependency.moduleName.substringBefore("/", ""),
            name = dependency.moduleName.substringAfter("/"),
            version = dependency.children.version
        )

    override fun dependenciesFor(dependency: PackageInfo): List<PackageInfo> =
        dependency.children.dependencies.flatMap(::packageInfoFor)

    override fun linkageFor(dependency: PackageInfo): PackageLinkage =
        if (dependency.isProject) PackageLinkage.PROJECT_DYNAMIC else PackageLinkage.DYNAMIC

    override fun createPackage(dependency: PackageInfo, issues: MutableCollection<Issue>): Package? {
        val packageJson = packageJsonForModuleId[dependency.moduleId]?.takeUnless { dependency.isProject }
            ?: return null

        return parsePackage(packageJson, moduleInfoResolver)
    }

    /**
     * Obtain the [PackageInfo] objects for the given [dependency], see [resolvePackageInfos]. Results are cached in
     * [packageInfosForDependency].
     */
    internal fun packageInfoFor(dependency: PackageInfo.Dependency): List<PackageInfo> =
        packageInfosForDependency.getOrPut(dependency) { resolvePackageInfos(dependency) }

    /**
     * Resolve the given [dependency] to the [PackageInfo] objects it refers to. Usually the result is a single
     * object, but in case of an ambiguity, all candidates are returned.
     *
     * Try the `realLocator` first to correctly handle virtual packages. If that fails, try to construct the real
     * locator from the virtual package's actual resolved version (handles virtual packages whose `children.version`
     * was overridden by Yarn's `resolutions` feature).
     *
     * If both targeted lookups fail, fall back to searching the map for all installed non-virtual, non-project
     * versions of the same module by name. This handles the case where Yarn's `resolutions` feature (or similar
     * mechanisms) cause a non-virtual dependency locator to reference a version that is not present in the map,
     * while a different version of the same module was actually installed. The candidates found by name are first
     * narrowed down to those matching the semver range from the [dependency]'s descriptor (keeping all versions if
     * the range cannot be determined or nothing matches it), and then to those of the locator's patch or non-patch
     * type (keeping the other type if none matches). The version is checked before the type, as attributing the
     * correct version has priority. The type check handles packages that exist both as a plain npm package and as a
     * compat-patched variant (e.g. via `@yarnpkg/plugin-compat`), which would otherwise lead to ambiguous
     * resolution.
     *
     * If the result is still not unique after all fallbacks, all remaining candidates are returned, as Yarn actually
     * installs all of them in parallel. An exception is only thrown if no candidate for the module exists at all.
     */
    private fun resolvePackageInfos(dependency: PackageInfo.Dependency): List<PackageInfo> {
        packageInfoForLocator[dependency.realLocator]?.let { return listOf(it) }

        // Fallback for virtual packages: derive the real locator from the virtual package's resolved version.
        packageInfoForLocator[dependency.locator]?.let { virtualInfo ->
            val moduleName = Locator.parse(dependency.locator).moduleName
            packageInfoForLocator["$moduleName@npm:${virtualInfo.children.version}"]?.let { return listOf(it) }
        }

        // Fallback for version mismatches caused by Yarn's `resolutions` feature: find installed versions of the
        // same module by name, ignoring the exact version in the locator.
        val realLocator = Locator.parse(dependency.realLocator)
        val moduleName = realLocator.moduleName
        val installed = packageInfoForLocator.values.filter {
            it.moduleName == moduleName && !it.isProject && !it.isVirtual
        }

        if (installed.isEmpty()) {
            error(
                "Could not find a PackageInfo for locator '${dependency.realLocator}'. No entry for module " +
                    "'$moduleName' exists in ${packageInfoForLocator.keys}."
            )
        }

        // Narrow down by the descriptor's semver range first, as attributing the correct version has priority over
        // matching the locator's patch or non-patch type.
        val versionMatches = installed.matchingVersionRange(dependency)?.ifEmpty { null } ?: installed

        // Prefer candidates of the locator's patch or non-patch type, falling back to the other type if none match.
        val candidates = versionMatches.filter { it.isPatch == realLocator.isPatch }.ifEmpty { versionMatches }

        logger.debug {
            if (candidates.size == 1) {
                "Resolved locator '${dependency.realLocator}' to '${candidates.single().value}' via module name " +
                    "lookup on descriptor '${dependency.descriptor}'."
            } else {
                "Could not unambiguously map the locator '${dependency.realLocator}' to one of the installed " +
                    "versions of '$moduleName'. Adding all candidates to the dependency graph: " +
                    "${candidates.map { it.value }}."
            }
        }

        return candidates
    }
}

internal val PackageInfo.isProject: Boolean
    get() = Locator.parse(value).isProject

internal val PackageInfo.isVirtual: Boolean
    get() = Locator.parse(value).isVirtual

internal val PackageInfo.isPatch: Boolean
    get() = Locator.parse(value).isPatch

internal val PackageInfo.moduleName: String
    get() = Locator.parse(value).moduleName

internal val PackageInfo.moduleId: String
    get() = buildString {
        append(moduleName)

        if (children.version.isNotBlank()) {
            append("@")
            append(children.version)
        }
    }

internal data class Locator(
    val moduleName: String,
    val remainder: String
) {
    companion object {
        fun parse(value: String): Locator {
            val moduleNameEndIndex = value.indexOf("@", startIndex = 1)
            return Locator(
                moduleName = value.take(moduleNameEndIndex),
                remainder = value.substring(moduleNameEndIndex + 1)
            )
        }
    }

    val isProject: Boolean = remainder.startsWith("workspace:") ||
        (remainder.startsWith("virtual:") && "#workspace:" in remainder)

    val isVirtual: Boolean = remainder.startsWith("virtual:") && !isProject

    val isPatch: Boolean = remainder.startsWith("patch:")
}

/**
 * Narrow this collection down to the entries whose version matches the semver range of the given [dependency]'s
 * `npm:` or `patch:` descriptor. Return `null` if the range cannot be determined, or the (possibly empty or still
 * not unique) list of matching entries otherwise.
 */
private fun Collection<PackageInfo>.matchingVersionRange(dependency: PackageInfo.Dependency): List<PackageInfo>? {
    val descriptorRemainder = Locator.parse(dependency.descriptor).remainder
    val rangeSpec = descriptorRemainder.withoutPrefix("npm:")
        ?: extractNpmRangeFromPatchRemainder(descriptorRemainder)
        ?: return null

    return runCatching { RangeListFactory.create(rangeSpec) }.getOrNull()?.let { range ->
        filter { candidate -> Semver.coerce(candidate.children.version)?.let { range.isSatisfiedBy(it) } == true }
    }
}

/**
 * Extract the npm version range embedded in a [patchRemainder] like
 * `patch:resolve@npm%3A^2.0.0-next.5#optional!builtin<compat/resolve>`, returning `^2.0.0-next.5`, or `null` if
 * [patchRemainder] is not a patch descriptor or the range cannot be extracted.
 */
private fun extractNpmRangeFromPatchRemainder(patchRemainder: String): String? {
    if (!patchRemainder.startsWith("patch:")) return null
    // The npm version range follows "@npm%3A" (URL-encoded "@npm:") or "@npm:" and ends at "#".
    val afterNpmPrefix = patchRemainder.substringAfter("@npm%3A", "").ifEmpty {
        patchRemainder.substringAfter("@npm:", "")
    }

    return afterNpmPrefix.takeIf { it.isNotEmpty() }?.substringBefore("#")
}
