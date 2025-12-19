/*
 * Copyright (C) 2025 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.utils.cyclonedx

import org.cyclonedx.model.Bom
import org.cyclonedx.model.Component

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Issue
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.utils.DependencyHandler

/**
 * A [DependencyHandler] for CycloneDX SBOM dependencies.
 */
class CycloneDxDependencyHandler : DependencyHandler<Component> {
    private val boms = linkedSetOf<Bom>()
    private val projectIds = mutableSetOf<Identifier>()

    fun registerProject(id: Identifier, bom: Bom) {
        projectIds += id
        boms += bom
    }

    private fun findComponent(bomRef: String): Component? =
        boms.firstNotNullOfOrNull { bom ->
            BomObjectLocator(bom, bomRef).locate()
                .takeIf { it.found() && it.isComponent() }
                ?.getObject() as? Component
        }

    private fun findDependencies(bomRef: String): List<Component> =
        boms.flatMap { it.dependencies.orEmpty() }
            .filter { it.ref == bomRef }
            .flatMap { it.dependencies?.map { dep -> dep.ref }.orEmpty() }
            .distinct()
            .map { ref ->
                requireNotNull(findComponent(ref)) {
                    "Could not find component for bom-ref '$ref' in the dependency graph."
                }
            }

    override fun identifierFor(dependency: Component): Identifier = dependency.toIdentifier()

    override fun dependenciesFor(dependency: Component): List<Component> =
        dependency.bomRef?.let { findDependencies(it) }.orEmpty()

    override fun linkageFor(dependency: Component): PackageLinkage =
        if (identifierFor(dependency) in projectIds) PackageLinkage.PROJECT_DYNAMIC else PackageLinkage.DYNAMIC

    override fun createPackage(dependency: Component, issues: MutableCollection<Issue>): Package? {
        if (identifierFor(dependency) in projectIds) return null
        return dependency.toPackage()
    }
}
