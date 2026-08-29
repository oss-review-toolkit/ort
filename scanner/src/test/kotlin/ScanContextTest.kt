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

package org.ossreviewtoolkit.scanner

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.PackageType
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.PathExclude
import org.ossreviewtoolkit.model.config.PathExcludeReason

class ScanContextTest : WordSpec({
    "the constructor" should {
        "throw if checkout paths is empty" {
            PackageType.entries.forAll { packageType ->
                shouldThrow<IllegalArgumentException> {
                    createScanContext(packageType = packageType, checkoutPaths = emptySet())
                }
            }
        }
    }

    "isPathExcluded() for a project of a root provenance" should {
        "return true if the file path relative to analysis root is excluded" {
            val context = createScanContext(
                packageType = PackageType.PROJECT,
                pathExcludes = setOf("src/main.java"),
                checkoutPaths = setOf("")
            )

            context.isPathExcluded("src/main.java") shouldBe true
            context.isPathExcluded("src/main2.java") shouldBe false
        }
    }

    "isPathExcluded() for a project of a submodule provenance" should {
        "return true if the file path relative to analysis root is excluded" {
            val context = createScanContext(
                packageType = PackageType.PROJECT,
                pathExcludes = setOf("submodules/module-1-root/src/main.java"),
                checkoutPaths = setOf("submodules/module-1-root")
            )

            context.isPathExcluded("src/main.java") shouldBe true
            context.isPathExcluded("src/main2.java") shouldBe false
        }

        "return false if only a subset of the occurrences of a file in the source tree are excluded" {
            val context = createScanContext(
                packageType = PackageType.PROJECT,
                pathExcludes = setOf("submodules/module-1-root/src/main.java"),
                checkoutPaths = setOf(
                    "submodules/module-1-root",
                    "submodules/module-1-root-duplicate"
                )
            )

            context.isPathExcluded("src/main.java") shouldBe false
        }

        "return true if all occurrences of a file in the source tree are excluded" {
            val context = createScanContext(
                packageType = PackageType.PROJECT,
                pathExcludes = setOf(
                    "submodules/module-1-root/src/main.java",
                    "submodules/module-1-root-duplicate/src/main.java"
                ),
                checkoutPaths = setOf(
                    "submodules/module-1-root",
                    "submodules/module-1-root-duplicate"
                )
            )

            context.isPathExcluded("src/main.java") shouldBe true
        }

        "not interpret path excludes as being relative to the provenance root" {
            val context = createScanContext(
                packageType = PackageType.PROJECT,
                pathExcludes = setOf(
                    "src/main.java"
                ),
                checkoutPaths = setOf(
                    "submodules/module-1-root"
                )
            )

            context.isPathExcluded("src/main.java") shouldBe false
        }
    }

    "isPathExcluded() for a package scan" should {
        "always return false" {
            val context = createScanContext(
                packageType = PackageType.PACKAGE,
                pathExcludes = setOf("submodules/module-1-root/src/main.java"),
                checkoutPaths = setOf("submodules/module-1-root")
            )

            context.isPathExcluded("src/main.java") shouldBe false
        }
    }
})

private fun createScanContext(
    packageType: PackageType,
    pathExcludes: Set<String> = emptySet(),
    checkoutPaths: Set<String>
): ScanContext =
    ScanContext(
        labels = emptyMap(),
        packageType = packageType,
        excludes = Excludes(
            paths = pathExcludes.map {
                PathExclude(
                    pattern = it,
                    reason = PathExcludeReason.OTHER
                )
            }
        ),
        checkoutPaths = checkoutPaths
    )
