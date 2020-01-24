/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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

import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

class ScopeTest : WordSpec({
    "getDependencyTreeDepth()" should {
        "return 0 if it does not contain any package" {
            val scope = Scope(name = "test", dependencies = sortedSetOf())

            scope.getDependencyTreeDepth() shouldBe 0
        }

        "return 1 if it contains only direct dependencies" {
            val scope = Scope(
                name = "test",
                dependencies = sortedSetOf(
                    PackageReference(id = Identifier("a")),
                    PackageReference(id = Identifier("b"))
                )
            )

            scope.getDependencyTreeDepth() shouldBe 1
        }

        "return 2 if it contains a tree of height 2" {
            val scope = Scope(
                name = "test",
                dependencies = sortedSetOf(
                    PackageReference(
                        id = Identifier("a"),
                        dependencies = sortedSetOf(
                            PackageReference(id = Identifier("a1"))
                        )
                    )
                )
            )

            scope.getDependencyTreeDepth() shouldBe 2
        }

        "return 3 if it contains a tree of height 3" {
            val scope = Scope(
                name = "test",
                dependencies = sortedSetOf(
                    PackageReference(
                        id = Identifier("a"),
                        dependencies = sortedSetOf(
                            PackageReference(
                                id = Identifier("a1"),
                                dependencies = sortedSetOf(
                                    PackageReference(id = Identifier("a11")),
                                    PackageReference(id = Identifier("a12"))
                                )
                            )
                        )
                    ),
                    PackageReference(id = Identifier("b"))
                )
            )

            scope.getDependencyTreeDepth() shouldBe 3
        }
    }
})
