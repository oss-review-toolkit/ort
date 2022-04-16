/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.model

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.endWith

class PackageReferenceTest : WordSpec() {
    companion object {
        fun pkgRefFromIdStr(id: String, vararg dependencies: PackageReference) =
            PackageReference(Identifier(id), dependencies = dependencies.toSortedSet())
    }

    private val node111 = pkgRefFromIdStr("::node1_1_1")
    private val node11 = pkgRefFromIdStr("::node1_1", node111)
    private val node12 = pkgRefFromIdStr("::node1_2")
    private val node1 = pkgRefFromIdStr("::node1", node11, node12)
    private val node2 = pkgRefFromIdStr("::node2")
    private val node3 = pkgRefFromIdStr("::node3", node12)
    private val root = pkgRefFromIdStr("::root", node1, node2, node3)

    init {
        "findReferences" should {
            "find references to an existing id" {
                root.findReferences(Identifier("::node1_2")) should containExactly(node12, node12)
                root.findReferences(Identifier("::node1")) should containExactly(node1)
            }

            "find no references to a non-existing id" {
                root.findReferences(Identifier("::nodeX_Y_Z")) should beEmpty()
                root.findReferences(Identifier("")) should beEmpty()
            }
        }

        "traverse" should {
            "visit each node of the tree depth-first" {
                val expectedOrder = mutableListOf(node111, node11, node12, node1, node2, node12, node3, root)

                root.traverse {
                    val expectedNode = expectedOrder.removeAt(0)
                    it shouldBe expectedNode
                    it
                }

                expectedOrder should beEmpty()
            }

            "change nodes as expected" {
                val modifiedTree = root.traverse {
                    val name = "${it.id.name}_suffix"
                    it.copy(
                        id = it.id.copy(name = name),
                        issues = listOf(OrtIssue(source = "test", message = "issue $name"))
                    )
                }

                modifiedTree.traverse {
                    it.id.name should endWith("_suffix")
                    it.issues should haveSize(1)
                    it.issues.first().message shouldBe "issue ${it.id.name}"
                    it
                }
            }
        }

        "visitNodes" should {
            "invoke the code block on the child dependencies" {
                val children = root.visitDependencies { it.toList() }

                children should containExactly(node1, node2, node3)
            }
        }
    }
}
