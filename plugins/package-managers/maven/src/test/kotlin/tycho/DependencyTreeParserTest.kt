/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.maven.tycho

import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk

import java.io.InputStream

import org.apache.maven.project.MavenProject

import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.graph.DependencyNode
import org.eclipse.aether.repository.RemoteRepository

private const val DEPENDENCY_TREE_JSON = """
{
   "groupId": "org.ossreviewtoolkit",
   "artifactId": "tycho-test-bundle",
   "version": "1.0.0-SNAPSHOT",
   "type": "pom",
   "scope": "",
   "classifier": "",
   "optional": "false",
   "children": [
     {
       "groupId": "org.apache.commons",
       "artifactId": "commons-configuration2",
       "version": "2.11.0",
       "type": "jar",
       "scope": "compile",
       "classifier": "",
       "optional": "false",
       "children": [
         {
           "groupId": "org.apache.commons",
           "artifactId": "commons-lang3",
           "version": "3.14.0",
           "type": "jar",
           "scope": "compile",
           "classifier": "",
           "optional": "false"
         },
         {
           "groupId": "org.apache.commons",
           "artifactId": "commons-text",
           "version": "1.12.0",
           "type": "bundle",
           "scope": "test",
           "classifier": "foo",
           "optional": "false"
         },
         {
           "groupId": "commons-logging",
           "artifactId": "commons-logging",
           "version": "1.3.2",
           "type": "jar",
           "scope": "compile",
           "classifier": "",
           "optional": "false"
         }
         ]
     }
     ]
 }
            """

class DependencyTreeParserTest : WordSpec({
    "JSON serialization" should {
        "deserialize a hierarchical structure" {
            val node = JSON.decodeFromString<DependencyTreeMojoNode>(DEPENDENCY_TREE_JSON)

            with(node) {
                groupId shouldBe "org.ossreviewtoolkit"
                artifactId shouldBe "tycho-test-bundle"
                version shouldBe "1.0.0-SNAPSHOT"
                type shouldBe "pom"
                scope shouldBe ""
                classifier shouldBe ""

                with(children.single()) {
                    groupId shouldBe "org.apache.commons"
                    artifactId shouldBe "commons-configuration2"
                    version shouldBe "2.11.0"
                    type shouldBe "jar"
                    scope shouldBe "compile"
                    classifier shouldBe ""

                    children should containExactlyInAnyOrder(
                        DependencyTreeMojoNode(
                            "org.apache.commons",
                            "commons-lang3",
                            "3.14.0",
                            "jar",
                            "compile",
                            ""
                        ),
                        DependencyTreeMojoNode(
                            "org.apache.commons",
                            "commons-text",
                            "1.12.0",
                            "bundle",
                            "test",
                            "foo"
                        ),
                        DependencyTreeMojoNode(
                            "commons-logging",
                            "commons-logging",
                            "1.3.2",
                            "jar",
                            "compile",
                            ""
                        )
                    )
                }
            }
        }
    }

    "parseDependencyTree()" should {
        "parse the dependency tree of a single project" {
            val projectNode = DependencyTreeMojoNode(
                "org.ossreviewtoolkit",
                "ort",
                "1.2.3-SNAPSHOT",
                "pom",
                "",
                "",
                listOf(
                    DependencyTreeMojoNode(
                        "org.apache.commons",
                        "commons-configuration2",
                        "2.11.0",
                        "jar",
                        "compile",
                        "",
                        listOf(
                            DependencyTreeMojoNode(
                                "org.apache.commons",
                                "commons-lang3",
                                "3.14.0",
                                "jar",
                                "compile",
                                ""
                            ),
                            DependencyTreeMojoNode(
                                "org.apache.commons",
                                "commons-text",
                                "1.12.0",
                                "bundle",
                                "test",
                                "foo"
                            ),
                            DependencyTreeMojoNode(
                                "commons-logging",
                                "commons-logging",
                                "1.3.2",
                                "jar",
                                "compile",
                                ""
                            )
                        )
                    )
                )
            )

            val projectDependencies = parseDependencyTree(
                inputStreamFor(projectNode),
                listOf(createProject("ort")),
                dummyFeatureFun
            ).toList()

            projectDependencies.shouldBeSingleton {
                compareNodes(projectNode, it)
            }
        }

        "parse the dependency trees of multiple projects" {
            val projectNode1 = DependencyTreeMojoNode(
                "org.ossreviewtoolkit",
                "module1",
                "1.2.3-SNAPSHOT",
                "pom",
                "",
                "",
                listOf(
                    DependencyTreeMojoNode(
                        "org.apache.commons",
                        "commons-configuration2",
                        "2.11.0",
                        "jar",
                        "compile",
                        ""
                    )
                )
            )
            val projectNode2 = DependencyTreeMojoNode(
                "org.ossreviewtoolkit",
                "module2",
                "1.2.3-SNAPSHOT",
                "eclipse-plugin",
                "",
                "",
                listOf(
                    DependencyTreeMojoNode(
                        "org.apache.commons",
                        "commons-lang3",
                        "3.14.0",
                        "jar",
                        "compile",
                        ""
                    )
                )
            )

            val projectDependencies = parseDependencyTree(
                inputStreamFor(projectNode1, projectNode2),
                listOf(createProject("module1"), createProject("module2", "eclipse-plugin")),
                dummyFeatureFun
            ).toList()

            projectDependencies shouldHaveSize 2
            compareNodes(projectNode1, projectDependencies.first())
            compareNodes(projectNode2, projectDependencies.last())
        }

        "use the available repositories" {
            val remoteRepo1 = mockk<RemoteRepository>()
            val remoteRepo2 = mockk<RemoteRepository>()
            val remoteRepositories = listOf(remoteRepo1, remoteRepo2)
            val project = spyk(createProject("module1", "eclipse-plugin")) {
                every { remoteProjectRepositories } returns remoteRepositories
            }

            val projectNode = DependencyTreeMojoNode(
                "org.ossreviewtoolkit",
                "module1",
                "1.2.3-SNAPSHOT",
                "eclipse-plugin",
                "",
                "",
                listOf(
                    DependencyTreeMojoNode(
                        "org.apache.commons",
                        "commons-configuration2",
                        "2.11.0",
                        "jar",
                        "compile",
                        ""
                    ),
                    DependencyTreeMojoNode(
                        "org.apache.commons",
                        "commons-lang3",
                        "3.14.0",
                        "jar",
                        "compile",
                        ""
                    )
                )
            )

            val dependencies = parseDependencyTree(
                inputStreamFor(projectNode),
                listOf(project),
                dummyFeatureFun
            ).single()

            dependencies.children.forAll { node ->
                node.repositories shouldContainExactly remoteRepositories
            }
        }

        "skip unknown projects" {
            val projectNode1 = DependencyTreeMojoNode(
                "org.ossreviewtoolkit",
                "module1",
                "1.2.3-SNAPSHOT",
                "pom",
                "",
                "",
                listOf(
                    DependencyTreeMojoNode(
                        "org.apache.commons",
                        "commons-configuration2",
                        "2.11.0",
                        "jar",
                        "compile",
                        ""
                    )
                )
            )
            val projectNode2 = DependencyTreeMojoNode(
                "org.ossreviewtoolkit",
                "module2",
                "1.2.3-SNAPSHOT",
                "eclipse-plugin",
                "",
                "",
                listOf(
                    DependencyTreeMojoNode(
                        "org.apache.commons",
                        "commons-lang3",
                        "3.14.0",
                        "jar",
                        "compile",
                        ""
                    )
                )
            )

            val projectDependencies = parseDependencyTree(
                inputStreamFor(projectNode1, projectNode2),
                listOf(createProject("module1")),
                dummyFeatureFun
            ).toList()

            projectDependencies.shouldBeSingleton {
                compareNodes(projectNode1, it)
            }
        }

        "remove source code bundles in dependencies" {
            val projectNode = DependencyTreeMojoNode(
                "org.ossreviewtoolkit",
                "module",
                "1.2.3-SNAPSHOT",
                "pom",
                "",
                "",
                listOf(
                    DependencyTreeMojoNode(
                        "org.apache.commons",
                        "commons-configuration2",
                        "2.11.0",
                        "jar",
                        "compile",
                        ""
                    ),
                    DependencyTreeMojoNode(
                        "p2.eclipse.plugin",
                        "org.objectweb.asm",
                        "9.1.0",
                        "jar",
                        "compile",
                        ""
                    ),
                    DependencyTreeMojoNode(
                        "p2.eclipse.plugin",
                        "org.objectweb.asm.source",
                        "9.1.0",
                        "jar",
                        "compile",
                        ""
                    )
                )
            )

            val projectDependencies = parseDependencyTree(
                inputStreamFor(projectNode),
                listOf(createProject("module")),
                dummyFeatureFun
            ).toList()

            projectDependencies.shouldBeSingleton { node ->
                node.children.map { it.artifact.artifactId } should containExactlyInAnyOrder(
                    "commons-configuration2",
                    "org.objectweb.asm"
                )
            }
        }

        "only remove source code bundles if there is a matching regular bundle" {
            val projectNode = DependencyTreeMojoNode(
                "org.ossreviewtoolkit",
                "module",
                "1.2.3-SNAPSHOT",
                "pom",
                "",
                "",
                listOf(
                    DependencyTreeMojoNode(
                        "org.apache.commons",
                        "commons-configuration2",
                        "2.11.0",
                        "jar",
                        "compile",
                        ""
                    ),
                    DependencyTreeMojoNode(
                        "p2.eclipse.plugin",
                        "org.objectweb.asm.source",
                        "9.1.0",
                        "jar",
                        "compile",
                        ""
                    )
                )
            )

            val projectDependencies = parseDependencyTree(
                inputStreamFor(projectNode),
                listOf(createProject("module")),
                dummyFeatureFun
            ).toList()

            projectDependencies.shouldBeSingleton { node ->
                node.children.map { it.artifact.artifactId } should containExactlyInAnyOrder(
                    "commons-configuration2",
                    "org.objectweb.asm.source"
                )
            }
        }

        "remove features from the dependency tree" {
            val projectNode = DependencyTreeMojoNode(
                "org.ossreviewtoolkit",
                "module",
                "1.2.3-SNAPSHOT",
                "pom",
                "",
                "",
                listOf(
                    DependencyTreeMojoNode(
                        "org.apache.commons",
                        "commons-configuration2",
                        "2.11.0",
                        "jar",
                        "compile",
                        ""
                    ),
                    DependencyTreeMojoNode(
                        "p2.eclipse.plugin",
                        "org.objectweb.asm",
                        "9.1.0",
                        "jar",
                        "compile",
                        ""
                    ),
                    DependencyTreeMojoNode(
                        "p2.eclipse.feature",
                        "org.objectweb.asm.feature",
                        "9.1.0",
                        "jar",
                        "compile",
                        ""
                    )
                )
            )

            val projectDependencies = parseDependencyTree(
                inputStreamFor(projectNode),
                listOf(createProject("module"))
            ) {
                it.groupId == "p2.eclipse.feature"
            }.toList()

            projectDependencies.shouldBeSingleton { node ->
                node.children.map { it.artifact.artifactId } should containExactlyInAnyOrder(
                    "commons-configuration2",
                    "org.objectweb.asm"
                )
            }
        }
    }
})

/**
 * Return an [InputStream] with the JSON representation of the given [projects] in the same way as this would be done
 * by the dependency tree plugin.
 */
private fun inputStreamFor(vararg projects: DependencyTreeMojoNode): InputStream =
    projects.joinToString("\n") { JSON.encodeToString(it) }.byteInputStream()

/**
 * Compare a hierarchy of [DependencyTreeMojoNode]s with a hierarchy of [DependencyNode]s given the root nodes
 * [mojoNode] and [dependencyNode].
 */
private fun compareNodes(mojoNode: DependencyTreeMojoNode, dependencyNode: DependencyNode) {
    with(dependencyNode) {
        with(artifact) {
            groupId shouldBe mojoNode.groupId
            artifactId shouldBe mojoNode.artifactId
            version shouldBe mojoNode.version
            extension shouldBe mojoNode.type
            classifier shouldBe mojoNode.classifier
        }

        dependency.scope shouldBe mojoNode.scope

        mojoNode.children.size shouldBe children.orEmpty().size
        mojoNode.children.zip(children.orEmpty()).forAll { (mojoChild, child) ->
            compareNodes(mojoChild, child)
        }
    }
}

/**
 * Create a [MavenProject] with the given [name] and optional [packaging].
 */
private fun createProject(name: String, packaging: String = "pom"): MavenProject =
    MavenProject().apply {
        artifactId = name
        groupId = "org.ossreviewtoolkit"
        version = "1.2.3-SNAPSHOT"
        this.packaging = packaging
    }

/**
 * A function that can be used as feature filter function if this functionality is not relevant for a test. It always
 * returns *false*.
 */
private val dummyFeatureFun: (Artifact) -> Boolean = { false }
