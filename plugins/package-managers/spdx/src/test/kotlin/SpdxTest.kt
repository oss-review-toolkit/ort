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

package org.ossreviewtoolkit.plugins.packagemanagers.spdx

import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.analyzer.withResolvedScopes
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.Includes

class SpdxTest : WordSpec({
    "resolveDependencies()" should {
        "return results with issues if all definition files fail to parse" {
            val analysisRoot = tempdir()
            val definitionFile = analysisRoot.resolve("broken.spdx.json").apply { writeText("not JSON") }

            val result = Spdx().resolveDependencies(
                analysisRoot,
                listOf(definitionFile),
                Excludes.EMPTY,
                Includes.EMPTY,
                AnalyzerConfiguration(),
                emptyMap()
            )

            val projectResults = result.projectResults.getValue(definitionFile)
            projectResults should haveSize(1)
            projectResults.single().issues should haveSize(1)
            result.sharedPackages should beEmpty()
        }

        "add the sources of the build producing the project package to the build scope" {
            val analysisRoot = tempdir()
            val definitionFile = analysisRoot.resolve("project.spdx.json").apply {
                writeText(
                    """
                    {
                      "@context": "https://spdx.org/rdf/3.0.1/spdx-context.jsonld",
                      "@graph": [
                        {
                          "type": "CreationInfo",
                          "@id": "_:creationInfo",
                          "created": "2026-01-01T00:00:00Z",
                          "createdBy": ["https://example.org/spdx/agent"],
                          "specVersion": "3.0.1"
                        },
                        {
                          "type": "Agent",
                          "spdxId": "https://example.org/spdx/agent",
                          "name": "agent",
                          "creationInfo": "_:creationInfo"
                        },
                        {
                          "type": "SpdxDocument",
                          "spdxId": "https://example.org/spdx/document",
                          "name": "document",
                          "creationInfo": "_:creationInfo",
                          "rootElement": ["https://example.org/spdx/project"]
                        },
                        {
                          "type": "software_Package",
                          "spdxId": "https://example.org/spdx/project",
                          "name": "project",
                          "creationInfo": "_:creationInfo"
                        },
                        {
                          "type": "software_Package",
                          "spdxId": "https://example.org/spdx/source",
                          "name": "source",
                          "creationInfo": "_:creationInfo"
                        },
                        {
                          "type": "software_Package",
                          "spdxId": "https://example.org/spdx/tool-source",
                          "name": "tool-source",
                          "creationInfo": "_:creationInfo"
                        },
                        {
                          "type": "build_Build",
                          "spdxId": "https://example.org/spdx/project-build",
                          "build_buildType": "https://example.org/build",
                          "creationInfo": "_:creationInfo"
                        },
                        {
                          "type": "build_Build",
                          "spdxId": "https://example.org/spdx/tool-build",
                          "build_buildType": "https://example.org/build",
                          "creationInfo": "_:creationInfo"
                        },
                        {
                          "type": "Relationship",
                          "spdxId": "https://example.org/spdx/relationship-1",
                          "from": "https://example.org/spdx/project-build",
                          "relationshipType": "hasOutput",
                          "to": ["https://example.org/spdx/project"],
                          "creationInfo": "_:creationInfo"
                        },
                        {
                          "type": "Relationship",
                          "spdxId": "https://example.org/spdx/relationship-2",
                          "from": "https://example.org/spdx/project-build",
                          "relationshipType": "hasInput",
                          "to": ["https://example.org/spdx/source"],
                          "creationInfo": "_:creationInfo"
                        },
                        {
                          "type": "LifecycleScopedRelationship",
                          "spdxId": "https://example.org/spdx/relationship-3",
                          "from": "https://example.org/spdx/project-build",
                          "relationshipType": "dependsOn",
                          "to": ["https://example.org/spdx/tool-build"],
                          "scope": "build",
                          "creationInfo": "_:creationInfo"
                        },
                        {
                          "type": "Relationship",
                          "spdxId": "https://example.org/spdx/relationship-4",
                          "from": "https://example.org/spdx/tool-build",
                          "relationshipType": "hasInput",
                          "to": ["https://example.org/spdx/tool-source"],
                          "creationInfo": "_:creationInfo"
                        }
                      ]
                    }
                    """.trimIndent()
                )
            }

            val result = Spdx().resolveDependencies(
                analysisRoot,
                listOf(definitionFile),
                Excludes.EMPTY,
                Includes.EMPTY,
                AnalyzerConfiguration(),
                emptyMap()
            )

            val projectResult = result.projectResults.getValue(definitionFile).single()
            projectResult.issues should beEmpty()

            val project = projectResult.project.withResolvedScopes(result.dependencyGraph)
            val buildScope = project.scopes.single()
            buildScope.name shouldBe "build"
            buildScope.dependencies.map { it.id.name } shouldBe listOf("source")
            buildScope.dependencies.single().dependencies.map { it.id.name } shouldBe listOf("tool-source")
        }

        "add packages the build producing the project package directly depends on to the build scope" {
            val analysisRoot = tempdir()
            val definitionFile = analysisRoot.resolve("project.spdx.json").apply {
                writeText(
                    """
                    {
                      "@context": "https://spdx.org/rdf/3.0.1/spdx-context.jsonld",
                      "@graph": [
                        {
                          "type": "CreationInfo",
                          "@id": "_:creationInfo",
                          "created": "2026-01-01T00:00:00Z",
                          "createdBy": ["https://example.org/spdx/agent"],
                          "specVersion": "3.0.1"
                        },
                        {
                          "type": "Agent",
                          "spdxId": "https://example.org/spdx/agent",
                          "name": "agent",
                          "creationInfo": "_:creationInfo"
                        },
                        {
                          "type": "SpdxDocument",
                          "spdxId": "https://example.org/spdx/document",
                          "name": "document",
                          "creationInfo": "_:creationInfo",
                          "rootElement": ["https://example.org/spdx/project"]
                        },
                        {
                          "type": "software_Package",
                          "spdxId": "https://example.org/spdx/project",
                          "name": "project",
                          "creationInfo": "_:creationInfo"
                        },
                        {
                          "type": "software_Package",
                          "spdxId": "https://example.org/spdx/compiler",
                          "name": "compiler",
                          "creationInfo": "_:creationInfo"
                        },
                        {
                          "type": "software_Package",
                          "spdxId": "https://example.org/spdx/compiler-lib",
                          "name": "compiler-lib",
                          "creationInfo": "_:creationInfo"
                        },
                        {
                          "type": "build_Build",
                          "spdxId": "https://example.org/spdx/project-build",
                          "build_buildType": "https://example.org/build",
                          "creationInfo": "_:creationInfo"
                        },
                        {
                          "type": "Relationship",
                          "spdxId": "https://example.org/spdx/relationship-1",
                          "from": "https://example.org/spdx/project-build",
                          "relationshipType": "hasOutput",
                          "to": ["https://example.org/spdx/project"],
                          "creationInfo": "_:creationInfo"
                        },
                        {
                          "type": "LifecycleScopedRelationship",
                          "spdxId": "https://example.org/spdx/relationship-2",
                          "from": "https://example.org/spdx/project-build",
                          "relationshipType": "dependsOn",
                          "to": ["https://example.org/spdx/compiler"],
                          "scope": "build",
                          "creationInfo": "_:creationInfo"
                        },
                        {
                          "type": "Relationship",
                          "spdxId": "https://example.org/spdx/relationship-3",
                          "from": "https://example.org/spdx/compiler",
                          "relationshipType": "dependsOn",
                          "to": ["https://example.org/spdx/compiler-lib"],
                          "creationInfo": "_:creationInfo"
                        }
                      ]
                    }
                    """.trimIndent()
                )
            }

            val result = Spdx().resolveDependencies(
                analysisRoot,
                listOf(definitionFile),
                Excludes.EMPTY,
                Includes.EMPTY,
                AnalyzerConfiguration(),
                emptyMap()
            )

            val projectResult = result.projectResults.getValue(definitionFile).single()
            projectResult.issues should beEmpty()

            val project = projectResult.project.withResolvedScopes(result.dependencyGraph)
            val buildScope = project.scopes.single()
            buildScope.name shouldBe "build"
            buildScope.dependencies.map { it.id.name } shouldBe listOf("compiler")
            buildScope.dependencies.single().dependencies.map { it.id.name } shouldBe listOf("compiler-lib")
        }

        "replace colons in the name and version of the project" {
            val analysisRoot = tempdir()
            val definitionFile = analysisRoot.resolve("project.spdx.json").apply {
                writeText(
                    """
                    {
                      "@context": "https://spdx.org/rdf/3.0.1/spdx-context.jsonld",
                      "@graph": [
                        {
                          "type": "CreationInfo",
                          "@id": "_:creationInfo",
                          "created": "2026-01-01T00:00:00Z",
                          "createdBy": ["https://example.org/spdx/agent"],
                          "specVersion": "3.0.1"
                        },
                        {
                          "type": "Agent",
                          "spdxId": "https://example.org/spdx/agent",
                          "name": "agent",
                          "creationInfo": "_:creationInfo"
                        },
                        {
                          "type": "SpdxDocument",
                          "spdxId": "https://example.org/spdx/document",
                          "name": "document",
                          "creationInfo": "_:creationInfo",
                          "rootElement": ["https://example.org/spdx/project"]
                        },
                        {
                          "type": "software_Package",
                          "spdxId": "https://example.org/spdx/project",
                          "name": "my:app",
                          "software_packageVersion": "1:2.3",
                          "creationInfo": "_:creationInfo"
                        }
                      ]
                    }
                    """.trimIndent()
                )
            }

            val result = Spdx().resolveDependencies(
                analysisRoot,
                listOf(definitionFile),
                Excludes.EMPTY,
                Includes.EMPTY,
                AnalyzerConfiguration(),
                emptyMap()
            )

            val projectResult = result.projectResults.getValue(definitionFile).single()
            projectResult.issues should beEmpty()
            projectResult.project.id shouldBe Identifier("SPDX", "", "my_app", "1_2.3")
        }

        "add dependencies in other lifecycle scopes to scopes of the same name" {
            val analysisRoot = tempdir()
            val definitionFile = analysisRoot.resolve("project.spdx.json").apply {
                writeText(
                    """
                    {
                      "@context": "https://spdx.org/rdf/3.0.1/spdx-context.jsonld",
                      "@graph": [
                        {
                          "type": "CreationInfo",
                          "@id": "_:creationInfo",
                          "created": "2026-01-01T00:00:00Z",
                          "createdBy": ["https://example.org/spdx/agent"],
                          "specVersion": "3.0.1"
                        },
                        {
                          "type": "Agent",
                          "spdxId": "https://example.org/spdx/agent",
                          "name": "agent",
                          "creationInfo": "_:creationInfo"
                        },
                        {
                          "type": "SpdxDocument",
                          "spdxId": "https://example.org/spdx/document",
                          "name": "document",
                          "creationInfo": "_:creationInfo",
                          "rootElement": ["https://example.org/spdx/project"]
                        },
                        {
                          "type": "software_Package",
                          "spdxId": "https://example.org/spdx/project",
                          "name": "project",
                          "creationInfo": "_:creationInfo"
                        },
                        {
                          "type": "software_Package",
                          "spdxId": "https://example.org/spdx/test-lib",
                          "name": "test-lib",
                          "creationInfo": "_:creationInfo"
                        },
                        {
                          "type": "software_Package",
                          "spdxId": "https://example.org/spdx/test-lib-dep",
                          "name": "test-lib-dep",
                          "creationInfo": "_:creationInfo"
                        },
                        {
                          "type": "LifecycleScopedRelationship",
                          "spdxId": "https://example.org/spdx/relationship-1",
                          "from": "https://example.org/spdx/project",
                          "relationshipType": "dependsOn",
                          "to": ["https://example.org/spdx/test-lib"],
                          "scope": "test",
                          "creationInfo": "_:creationInfo"
                        },
                        {
                          "type": "Relationship",
                          "spdxId": "https://example.org/spdx/relationship-2",
                          "from": "https://example.org/spdx/test-lib",
                          "relationshipType": "dependsOn",
                          "to": ["https://example.org/spdx/test-lib-dep"],
                          "creationInfo": "_:creationInfo"
                        }
                      ]
                    }
                    """.trimIndent()
                )
            }

            val result = Spdx().resolveDependencies(
                analysisRoot,
                listOf(definitionFile),
                Excludes.EMPTY,
                Includes.EMPTY,
                AnalyzerConfiguration(),
                emptyMap()
            )

            val projectResult = result.projectResults.getValue(definitionFile).single()
            projectResult.issues should beEmpty()

            val project = projectResult.project.withResolvedScopes(result.dependencyGraph)
            val testScope = project.scopes.single()
            testScope.name shouldBe "test"
            testScope.dependencies.map { it.id.name } shouldBe listOf("test-lib")
            testScope.dependencies.single().dependencies.map { it.id.name } shouldBe listOf("test-lib-dep")

            result.sharedPackages.map { it.id.name } should containExactlyInAnyOrder("test-lib", "test-lib-dep")
        }
    }

    "getEntryPoints()" should {
        "return the nodes that are not a dependency of other nodes" {
            val graph = mapOf(
                "A" to listOf("B"),
                "B" to listOf("C"),
                "C" to emptyList(),
                "D" to listOf("C")
            )

            getEntryPoints(graph.keys) { graph[it].orEmpty() } shouldBe listOf("A", "D")
        }

        "keep an entry point for a disconnected cycle" {
            val graph = mapOf(
                "A" to listOf("B"),
                "B" to listOf("A"),
                "C" to emptyList()
            )

            getEntryPoints(graph.keys) { graph[it].orEmpty() } shouldBe listOf("C", "A")
        }

        "keep an entry point if all nodes are part of a cycle" {
            val graph = mapOf(
                "A" to listOf("B"),
                "B" to listOf("C"),
                "C" to listOf("A")
            )

            getEntryPoints(graph.keys) { graph[it].orEmpty() } shouldBe listOf("A")
        }

        "keep an entry point for each of multiple disconnected cycles" {
            val graph = mapOf(
                "A" to listOf("B"),
                "B" to listOf("A"),
                "C" to listOf("D"),
                "D" to listOf("C")
            )

            getEntryPoints(graph.keys) { graph[it].orEmpty() } shouldBe listOf("A", "C")
        }

        "not add an entry point for a cycle that is reachable from another entry point" {
            val graph = mapOf(
                "A" to listOf("B"),
                "B" to listOf("C"),
                "C" to listOf("B")
            )

            getEntryPoints(graph.keys) { graph[it].orEmpty() } shouldBe listOf("A")
        }

        "replace an entry point that turns out to be reachable from a cycle" {
            val graph = mapOf(
                "C" to emptyList(),
                "A" to listOf("B", "C"),
                "B" to listOf("A")
            )

            // "C" is checked first and is not reachable from any other entry point, but it is reachable from "A".
            getEntryPoints(graph.keys) { graph[it].orEmpty() } shouldBe listOf("A")
        }

        "consider nodes that are only reachable via other nodes" {
            val graph = mapOf(
                "A" to listOf("X"),
                "X" to listOf("B"),
                "B" to emptyList()
            )

            // "B" is only a dependency of "X", which is not among the given nodes.
            getEntryPoints(listOf("A", "B")) { graph[it].orEmpty() } shouldBe listOf("A")
        }

        "consider cycles that are only closed via other nodes" {
            val graph = mapOf(
                "A" to listOf("X"),
                "X" to listOf("B"),
                "B" to listOf("A")
            )

            // "A" and "B" form a cycle via "X", which is not among the given nodes.
            getEntryPoints(listOf("A", "B")) { graph[it].orEmpty() } shouldBe listOf("A")
        }
    }
})
