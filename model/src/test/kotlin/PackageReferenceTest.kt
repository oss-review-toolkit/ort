/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package com.here.ort.model

import io.kotlintest.matchers.beEmpty
import io.kotlintest.matchers.endWith
import io.kotlintest.matchers.haveSize
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

class PackageReferenceTest : WordSpec() {
    companion object {
        fun pkgRefFromIdStr(id: String, vararg dependencies: PackageReference) =
            PackageReference(Identifier(id), dependencies = dependencies.toSortedSet())
    }

    private val node1_1_1 = pkgRefFromIdStr("::node1_1_1")
    private val node1_1 = pkgRefFromIdStr("::node1_1", node1_1_1)
    private val node1_2 = pkgRefFromIdStr("::node1_2")
    private val node1 = pkgRefFromIdStr("::node1", node1_1, node1_2)
    private val node2 = pkgRefFromIdStr("::node2")
    private val node3 = pkgRefFromIdStr("::node3", node1_2)
    private val root = pkgRefFromIdStr("::root", node1, node2, node3)

    init {
        "findReferences" should {
            "find references to an existing id" {
                root.findReferences(Identifier("::node1_2")) shouldBe listOf(node1_2, node1_2)
                root.findReferences(Identifier("::node1")) shouldBe listOf(node1)
            }

            "find no references to a non-existing id" {
                root.findReferences(Identifier("::nodeX_Y_Z")) should beEmpty()
                root.findReferences(Identifier("")) should beEmpty()
            }
        }

        "traverse" should {
            "visit each node of the tree depth-first" {
                val expectedOrder = mutableListOf(node1_1_1, node1_1, node1_2, node1, node2, node1_2, node3, root)

                root.traverse {
                    val expectedNode = expectedOrder.removeAt(0)
                    it shouldBe expectedNode
                    it
                }

                expectedOrder should haveSize(0)
            }

            "change nodes as expected" {
                val modifiedTree = root.traverse {
                    val name = "${it.id.name}_suffix"
                    it.copy(
                        id = it.id.copy(name = name),
                        errors = listOf(OrtIssue(source = "test", message = "error $name"))
                    )
                }

                modifiedTree.traverse {
                    it.id.name should endWith("_suffix")
                    it.errors should haveSize(1)
                    it.errors.first().message shouldBe "error ${it.id.name}"
                    it
                }
            }
        }
    }
}
