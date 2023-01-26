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

package org.ossreviewtoolkit.utils.test

import java.util.SortedSet

import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.AnalyzerRun
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.utils.ort.DeclaredLicenseProcessor

@DslMarker
annotation class OrtResultDsl

@OrtResultDsl
class OrtResultBuilder {
    private val projects = mutableSetOf<Project>()
    private val packages = mutableSetOf<Package>()
    private val parentChildIds = mutableMapOf<String, MutableSet<String>>()

    @OrtResultDsl
    inner class ProjectBuilder(private val id: String) {
        private val rootIds = mutableSetOf<String>()

        @OrtResultDsl
        var license = "Apache-2.0"

        @OrtResultDsl
        fun pkg(id: String, setup: PkgBuilder.() -> Unit): Package {
            rootIds += id
            val pkg = this@OrtResultBuilder.PkgBuilder(id).apply(setup).build()
            this@OrtResultBuilder.packages += pkg
            return pkg
        }

        private fun getDependencies(ids: Set<String>): SortedSet<PackageReference> =
            ids.mapTo(sortedSetOf()) {
                PackageReference(
                    id = Identifier(it),
                    dependencies = getDependencies(this@OrtResultBuilder.parentChildIds[it].orEmpty())
                )
            }

        fun build(): Project {
            val scope = Scope(
                name = "compile",
                dependencies = getDependencies(rootIds)
            )

            val declaredLicenses = setOf(license)

            return Project.EMPTY.copy(
                id = Identifier(id),
                declaredLicenses = declaredLicenses,
                declaredLicensesProcessed = DeclaredLicenseProcessor.process(declaredLicenses),
                scopeDependencies = sortedSetOf(scope)
            )
        }
    }

    @OrtResultDsl
    inner class PkgBuilder(private val id: String) {
        @OrtResultDsl
        var license = "Apache-2.0"

        @OrtResultDsl
        fun pkg(id: String, setup: PkgBuilder.() -> Unit): Package {
            this@OrtResultBuilder.parentChildIds.getOrPut(this@PkgBuilder.id) { mutableSetOf() } += id
            val pkg = this@OrtResultBuilder.PkgBuilder(id).apply(setup).build()
            this@OrtResultBuilder.packages += pkg
            return pkg
        }

        fun build(): Package {
            val declaredLicenses = setOf(license)

            return Package.EMPTY.copy(
                id = Identifier(id),
                declaredLicenses = declaredLicenses,
                declaredLicensesProcessed = DeclaredLicenseProcessor.process(declaredLicenses)
            )
        }
    }

    @OrtResultDsl
    fun project(id: String, setup: ProjectBuilder.() -> Unit): Project {
        val project = ProjectBuilder(id).apply(setup).build()
        projects += project
        return project
    }

    fun build(): OrtResult {
        return OrtResult.EMPTY.copy(
            analyzer = AnalyzerRun.EMPTY.copy(
                result = AnalyzerResult(
                    projects = projects,
                    packages = packages
                )
            )
        )
    }
}

@OrtResultDsl
fun ortResult(setup: OrtResultBuilder.() -> Unit) = OrtResultBuilder().apply(setup).build()
