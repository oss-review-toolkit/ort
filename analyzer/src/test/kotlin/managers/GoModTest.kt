/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

class GoModTest : WordSpec({
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
