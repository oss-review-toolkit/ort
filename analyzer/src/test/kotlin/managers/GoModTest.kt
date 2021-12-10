/*
 * Copyright (C) 2021 HERE Europe B.V.
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

package org.ossreviewtoolkit.analyzer.managers

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.VcsType

class GoModTest : WordSpec({
    "toVcsInfo" should {
        "return the VCS type 'Git'" {
            val id = Identifier("GoMod::github.com/chai2010/gettext-go:v1.0.0")

            id.toVcsInfo().type shouldBe VcsType.GIT
        }

        "return the revision" {
            val id = Identifier("GoMod::github.com/chai2010/gettext-go:v1.0.0")

            id.toVcsInfo().revision shouldBe "v1.0.0"
        }

        "return the VCS URL and path for a package from a single module repository" {
            val id = Identifier("GoMod::github.com/chai2010/gettext-go:v1.0.0")

            with(id.toVcsInfo()) {
                path shouldBe ""
                url shouldBe "https://github.com/chai2010/gettext-go.git"
            }
        }

        "return the VCS URL and path for a package from a mono repository" {
            val id = Identifier("GoMod::github.com/Azure/go-autorest/autorest/date:v0.1.0")

            with(id.toVcsInfo()) {
                path shouldBe "autorest/date"
                url shouldBe "https://github.com/Azure/go-autorest.git"
            }
        }

        "return the SHA1 from a 'pseudo version', when there is no known base version" {
            // See https://golang.org/ref/mod#pseudo-versions.
            val id = Identifier("GoMod::github.com/example/project:v0.0.0-20191109021931-daa7c04131f5")

            id.toVcsInfo().revision shouldBe "daa7c04131f5"
        }

        "return the SHA1 from a 'pseudo version', when base version is a release version" {
            // See https://golang.org/ref/mod#pseudo-versions.
            val id = Identifier(
                "GoMod::github.com/example/project:v0.8.1-0.20171018195549-f15c970de5b7"
            )

            id.toVcsInfo().revision shouldBe "f15c970de5b7"
        }

        "return the SHA1 from a 'pseudo version', when base version is a pre-release version" {
            // See https://golang.org/ref/mod#pseudo-versions.
            val id = Identifier("GoMod::github.com/example/project:v0.8.1-pre.0.20171018195549-f15c970de5b7")

            id.toVcsInfo().revision shouldBe "f15c970de5b7"
        }

        "return the SHA1 for a version with a '+incompatible' suffix" {
            val id = Identifier("GoMod::github.com/example/project:v43.3.0+incompatible")

            id.toVcsInfo().revision shouldBe "v43.3.0"
        }
    }

    "parseWhyOutput" should {
        "detect packages that are used" {
            val output = """
                # cloud.google.com/go
                foo
                
                # github.com/fatih/color
                bar
                
                # github.com/hashicorp/go-multierror
                baz
            """.trimIndent()

            parseWhyOutput(output) should containExactlyInAnyOrder(
                "cloud.google.com/go",
                "github.com/fatih/color",
                "github.com/hashicorp/go-multierror"
            )
        }

        "handle a missing current package" {
            val output = "foo"

            parseWhyOutput(output) should beEmpty()
        }

        "skip unused packages" {
            val output = """
                # cloud.google.com/go
                foo
                
                # github.com/fatih/color
                bar
                
                # github.com/hashicorp/go-multierror
                (main module does not need package github.com/hashicorp/go-multierror
            """.trimIndent()

            parseWhyOutput(output) should containExactlyInAnyOrder(
                "cloud.google.com/go",
                "github.com/fatih/color"
            )
        }
    }
})
