/*
 * Copyright (c) 2017 HERE Europe B.V.
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

import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.WordSpec

/**
 * Test cases for [Package].
 */
class PackageTest : WordSpec({
    "normalizedVcsUrl" should {
        "actually be normalized" {
            val pkg = Package(
                    packageManager = "",
                    namespace = "",
                    name = "",
                    version = "1.0.0",
                    declaredLicenses = emptySet(),
                    description = "",
                    homepageUrl = "",
                    downloadUrl = "",
                    hash = "",
                    hashAlgorithm = "",
                    vcsPath = "",
                    vcsProvider = "",
                    vcsUrl = "https://github.com/fb55/nth-check",
                    vcsRevision = ""
            )
            val expectedUrl = "https://github.com/fb55/nth-check.git"

            pkg.normalizedVcsUrl shouldBe expectedUrl
        }

        "should understand NPM shortcuts for NPM packages" {
            val pkg = Package(
                    packageManager = "NPM",
                    namespace = "",
                    name = "",
                    version = "1.0.0",
                    declaredLicenses = emptySet(),
                    description = "",
                    homepageUrl = "",
                    downloadUrl = "",
                    hash = "",
                    hashAlgorithm = "",
                    vcsPath = "",
                    vcsProvider = "",
                    vcsUrl = "npm/npm",
                    vcsRevision = ""
            )
            val expectedUrl = "https://github.com/npm/npm.git"

            pkg.normalizedVcsUrl shouldBe expectedUrl
        }
    }
})
