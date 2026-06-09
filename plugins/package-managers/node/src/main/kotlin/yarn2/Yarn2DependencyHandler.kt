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

internal class Yarn2DependencyHandler(
    private val moduleInfoResolver: ModuleInfoResolver
) : DependencyHandler<PackageInfo> {
    private lateinit var workingDir: File
    private val packageJsonForModuleId = mutableMapOf<String, PackageJson>()
    private val packageInfoForLocator = mutableMapOf<String, PackageInfo>()

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
    }

    override fun identifierFor(dependency: PackageInfo): Identifier =
        Identifier(
            type = with(NodePackageManagerType.YARN2) { if (dependency.isProject) projectType else packageType },
            namespace = dependency.moduleName.substringBefore("/", ""),
            name = dependency.moduleName.substringAfter("/"),
            version = dependency.children.version
        )

    override fun dependenciesFor(dependency: PackageInfo): List<PackageInfo> =
        dependency.children.dependencies.map(::packageInfoFor)

    override fun linkageFor(dependency: PackageInfo): PackageLinkage =
        if (dependency.isProject) PackageLinkage.PROJECT_DYNAMIC else PackageLinkage.DYNAMIC

    override fun createPackage(dependency: PackageInfo, issues: MutableCollection<Issue>): Package? {
        val packageJson = packageJsonForModuleId[dependency.moduleId]?.takeUnless { dependency.isProject }
            ?: return null

        return parsePackage(packageJson, moduleInfoResolver)
    }

    /**
     * Obtain the [PackageInfo] object for the given [dependency].
     *
     * Try the `realLocator` first to correctly handle virtual packages. If that fails, try to construct the real
     * locator from the virtual package's actual resolved version (handles virtual packages whose `children.version`
     * was overridden by Yarn's `resolutions` feature).
     *
     * If both targeted lookups fail, fall back to searching the map for all installed versions of the same module
     * by name. This handles the case where Yarn's `resolutions` feature (or similar mechanisms) cause a non-virtual
     * dependency locator to reference a version that is not present in the map, while a different version of the
     * same module was actually installed. If exactly one candidate is found, it is used. If multiple candidates are
     * found, the resolution is ambiguous and an exception is thrown.
     */
    internal fun packageInfoFor(dependency: PackageInfo.Dependency): PackageInfo {
        packageInfoForLocator[dependency.realLocator]?.let { return it }

        // Fallback for virtual packages: derive the real locator from the virtual package's resolved version.
        packageInfoForLocator[dependency.locator]?.let { virtualInfo ->
            val moduleName = Locator.parse(dependency.locator).moduleName
            packageInfoForLocator["$moduleName@npm:${virtualInfo.children.version}"]?.let { return it }
        }

        // Fallback for version mismatches caused by Yarn's `resolutions` feature: find the single installed version
        // of the same module by name, ignoring the exact version in the locator.
        val moduleName = Locator.parse(dependency.realLocator).moduleName
        val candidates = packageInfoForLocator.values.filter {
            it.moduleName == moduleName && !it.isProject && !it.isVirtual
        }

        candidates.singleOrNull()?.also {
            logger.debug {
                "Resolved locator '${dependency.realLocator}' to '${it.value}' via module name lookup."
            }

            return it
        }

        if (candidates.isEmpty()) {
            error(
                "Could not find a PackageInfo for locator '${dependency.realLocator}'. No entry for module " +
                    "'$moduleName' exists in ${packageInfoForLocator.keys}."
            )
        }

        error("Could not unambiguously resolve locator '${dependency.realLocator}'. Found ${candidates.size} " +
            "installed versions of module '$moduleName': ${candidates.map { it.value }}.")
    }
}

internal val PackageInfo.isProject: Boolean
    get() = Locator.parse(value).isProject

internal val PackageInfo.isVirtual: Boolean
    get() = Locator.parse(value).isVirtual

internal val PackageInfo.moduleName: String
    // TODO: Handle patched packages different than non-patched ones.
    // Patch packages have locators as e.g. the following, where the first component ends with "@patch".
    // resolve@patch:resolve@npm%3A1.22.8#optional!builtin<compat/resolve>::version=1.22.8&hash=c3c19d
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
}
