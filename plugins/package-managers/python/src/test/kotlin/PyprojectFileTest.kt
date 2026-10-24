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

package org.ossreviewtoolkit.plugins.packagemanagers.python

import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.utils.common.div

class PyprojectFileTest : WordSpec({
    "parsePyprojectFile()" should {
        "parse the project table" {
            val pyprojectFile = tempdir() / PYPROJECT_FILENAME

            pyprojectFile.writeText(
                """
                    [project]
                    name = "pylock-example"
                    version = "1.0.0"
                    description = "An example project."
                    license = "MIT"
                    authors = [
                        { name = "Example Author", email = "author@example.org" },
                        { name = "Nameless" },
                        { email = "mailonly@example.org" }
                    ]
                    classifiers = [
                        "Programming Language :: Python :: 3",
                        "License :: OSI Approved :: MIT License"
                    ]
                    [project.urls]
                    homepage = "https://example.org/pylock-example"
                    Repository = "https://github.com/example/pylock-example"

                    [tool.uv]
                    dev-dependencies = ["pytest"]
                """.trimIndent()
            )

            val project = parsePyprojectFile(pyprojectFile).shouldNotBeNull().project.shouldNotBeNull()

            project.name shouldBe "pylock-example"
            project.version shouldBe "1.0.0"
            project.description shouldBe "An example project."
            project.getDeclaredLicenses() should containExactlyInAnyOrder("MIT", "MIT License")
            project.getAuthors() should containExactlyInAnyOrder(
                "Example Author <author@example.org>",
                "Nameless",
                "mailonly@example.org"
            )
            project.getHomepageUrl() shouldBe "https://example.org/pylock-example"
        }

        "parse a legacy license table" {
            val pyprojectFile = tempdir() / PYPROJECT_FILENAME

            pyprojectFile.writeText(
                """
                    [project]
                    name = "legacy"
                    license = { text = "Apache-2.0" }
                """.trimIndent()
            )

            val project = parsePyprojectFile(pyprojectFile).shouldNotBeNull().project.shouldNotBeNull()

            project.getDeclaredLicenses() should containExactly("Apache-2.0")
        }

        "ignore a license file reference" {
            val pyprojectFile = tempdir() / PYPROJECT_FILENAME

            pyprojectFile.writeText(
                """
                    [project]
                    name = "legacy"
                    license = { file = "LICENSE" }
                """.trimIndent()
            )

            val project = parsePyprojectFile(pyprojectFile).shouldNotBeNull().project.shouldNotBeNull()

            project.getDeclaredLicenses() should beEmpty()
        }

        "fall back to Poetry's own metadata table" {
            val pyprojectFile = tempdir() / PYPROJECT_FILENAME

            pyprojectFile.writeText(
                """
                    [tool.poetry]
                    name = "rich"
                    homepage = "https://github.com/Textualize/rich"
                    version = "15.0.0"
                    description = "Render rich text to the terminal"
                    authors = ["Will McGugan <willmcgugan@gmail.com>", "Nameless"]
                    license = "MIT"
                    classifiers = ["License :: OSI Approved :: MIT License"]

                    [tool.poetry.dependencies]
                    python = ">=3.9.0"
                """.trimIndent()
            )

            val project = parsePyprojectFile(pyprojectFile).shouldNotBeNull().getProjectMetadata().shouldNotBeNull()

            project.name shouldBe "rich"
            project.version shouldBe "15.0.0"
            project.description shouldBe "Render rich text to the terminal"
            project.getDeclaredLicenses() should containExactlyInAnyOrder("MIT", "MIT License")
            project.getAuthors() should containExactlyInAnyOrder("Will McGugan <willmcgugan@gmail.com>", "Nameless")
            project.getHomepageUrl() shouldBe "https://github.com/Textualize/rich"
        }

        "prefer the project table over Poetry's metadata table and take missing values from the latter" {
            val pyprojectFile = tempdir() / PYPROJECT_FILENAME

            pyprojectFile.writeText(
                """
                    [project]
                    name = "modern"
                    version = "2.0.0"
                    dynamic = ["license", "authors"]

                    [tool.poetry]
                    name = "legacy"
                    version = "1.0.0"
                    license = "MIT"
                    authors = ["Jane Doe <jane@example.org>"]
                    homepage = "https://example.org/modern"
                """.trimIndent()
            )

            val project = parsePyprojectFile(pyprojectFile).shouldNotBeNull().getProjectMetadata().shouldNotBeNull()

            project.name shouldBe "modern"
            project.version shouldBe "2.0.0"
            project.getDeclaredLicenses() should containExactly("MIT")
            project.getAuthors() should containExactly("Jane Doe <jane@example.org>")
            project.getHomepageUrl() shouldBe "https://example.org/modern"
        }

        "return null for a non-existing file" {
            parsePyprojectFile(tempdir() / PYPROJECT_FILENAME) should beNull()
        }

        "return null for a broken file" {
            val pyprojectFile = tempdir() / PYPROJECT_FILENAME

            pyprojectFile.writeText("[project\nname = ")

            parsePyprojectFile(pyprojectFile) should beNull()
        }
    }
})
