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

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.cyclonedx.model.Bom
import org.cyclonedx.model.Component
import org.cyclonedx.model.Dependency

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.PackageLinkage

class CycloneDxDependencyHandlerTest : StringSpec({
    "identifierFor()" should {
        "delegate to Component.toIdentifier()" {
            val handler = CycloneDxDependencyHandler()
            val component = Component().apply {
                name = "cowboy"
                version = "2.9.0"
                purl = "pkg:hex/cowboy@2.9.0"
            }

            val identifier = handler.identifierFor(component)

            identifier.type shouldBe "hex"
            identifier.name shouldBe "cowboy"
            identifier.version shouldBe "2.9.0"
        }
    }

    "createPackage()" should {
        "delegate to Component.toPackage()" {
            val handler = CycloneDxDependencyHandler()
            val component = Component().apply {
                name = "cowboy"
                version = "2.9.0"
                purl = "pkg:hex/cowboy@2.9.0"
            }

            handler.createPackage(component, mutableListOf()).shouldNotBeNull {
                id.name shouldBe "cowboy"
                purl shouldBe "pkg:hex/cowboy@2.9.0"
            }
        }

        "return null for registered project IDs" {
            val handler = CycloneDxDependencyHandler()
            val component = Component().apply {
                bomRef = "ref-my-project"
                name = "my-project"
                version = "1.0.0"
                purl = "pkg:hex/my-project@1.0.0"
            }

            val bom = Bom().apply {
                components = listOf(component)
            }

            val projectId = Identifier("hex", "", "my-project", "1.0.0")
            handler.registerProject(projectId, bom)

            handler.createPackage(component, mutableListOf()) should beNull()
        }
    }

    "linkageFor()" should {
        "return DYNAMIC for regular dependencies" {
            val handler = CycloneDxDependencyHandler()
            val component = Component().apply {
                name = "cowboy"
                version = "2.9.0"
                purl = "pkg:hex/cowboy@2.9.0"
            }

            handler.linkageFor(component) shouldBe PackageLinkage.DYNAMIC
        }

        "return PROJECT_DYNAMIC for registered project IDs" {
            val handler = CycloneDxDependencyHandler()
            val component = Component().apply {
                bomRef = "ref-my-project"
                name = "my-project"
                version = "1.0.0"
                purl = "pkg:hex/my-project@1.0.0"
            }

            val bom = Bom().apply {
                components = listOf(component)
            }

            val projectId = Identifier("hex", "", "my-project", "1.0.0")
            handler.registerProject(projectId, bom)

            handler.linkageFor(component) shouldBe PackageLinkage.PROJECT_DYNAMIC
        }
    }

    "dependenciesFor()" should {
        "return empty list when component has no dependencies" {
            val handler = CycloneDxDependencyHandler()
            val component = Component().apply {
                bomRef = "ref-cowboy"
                name = "cowboy"
                version = "2.9.0"
            }

            val bom = Bom().apply {
                components = listOf(component)
                dependencies = listOf(Dependency(component.bomRef))
            }

            handler.registerProject(Identifier("hex", "", "test-project", "1.0.0"), bom)

            handler.dependenciesFor(component) should beEmpty()
        }

        "return child components from Bom dependencies" {
            val handler = CycloneDxDependencyHandler()

            val cowboy = Component().apply {
                bomRef = "ref-cowboy"
                name = "cowboy"
                version = "2.9.0"
            }

            val ranch = Component().apply {
                bomRef = "ref-ranch"
                name = "ranch"
                version = "1.8.0"
            }

            val bom = Bom().apply {
                components = listOf(cowboy, ranch)
                dependencies = listOf(
                    Dependency(cowboy.bomRef).apply {
                        dependencies = listOf(Dependency(ranch.bomRef))
                    },
                    Dependency(ranch.bomRef)
                )
            }

            handler.registerProject(Identifier("hex", "", "test-project", "1.0.0"), bom)

            val deps = handler.dependenciesFor(cowboy)
            deps shouldHaveSize 1
            deps.first().name shouldBe "ranch"
        }

        "return empty list for component without bomRef" {
            val handler = CycloneDxDependencyHandler()
            val component = Component().apply {
                name = "cowboy"
                version = "2.9.0"
            }

            handler.dependenciesFor(component) should beEmpty()
        }

        "aggregate components from multiple registered Boms" {
            val handler = CycloneDxDependencyHandler()

            val cowboy = Component().apply {
                bomRef = "ref-cowboy"
                name = "cowboy"
                version = "2.9.0"
            }

            val ranch = Component().apply {
                bomRef = "ref-ranch"
                name = "ranch"
                version = "1.8.0"
            }

            val bom1 = Bom().apply {
                components = listOf(cowboy)
                dependencies = listOf(
                    Dependency(cowboy.bomRef).apply {
                        dependencies = listOf(Dependency("ref-ranch"))
                    }
                )
            }

            val bom2 = Bom().apply {
                components = listOf(ranch)
                dependencies = listOf(Dependency(ranch.bomRef))
            }

            handler.registerProject(Identifier("hex", "", "project1", "1.0.0"), bom1)
            handler.registerProject(Identifier("hex", "", "project2", "1.0.0"), bom2)

            val deps = handler.dependenciesFor(cowboy)
            deps shouldHaveSize 1
            deps.first().name shouldBe "ranch"
        }
    }
})
