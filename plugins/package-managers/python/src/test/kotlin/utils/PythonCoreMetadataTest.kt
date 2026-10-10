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

package org.ossreviewtoolkit.plugins.packagemanagers.python.utils

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

class PythonCoreMetadataTest : WordSpec({
    "parse()" should {
        "parse the headers and ignore the description body" {
            val metadata = PythonCoreMetadata.parse(
                """
                    Metadata-Version: 2.4
                    Name: attrs
                    Version: 26.1.0
                    Summary: Classes Without Boilerplate
                    Project-URL: Documentation, https://www.attrs.org/
                    Project-URL: GitHub, https://github.com/python-attrs/attrs
                    Author-email: Hynek Schlawack <hs@ox.cx>
                    License-Expression: MIT
                    License-File: LICENSE
                    Classifier: Development Status :: 5 - Production/Stable
                    Classifier: License :: OSI Approved :: MIT License
                    Requires-Python: >=3.9
                    Description-Content-Type: text/markdown

                    # attrs

                    License: This is not a header, but the body.
                """.trimIndent(),
                source = "test"
            )

            metadata.summary shouldBe "Classes Without Boilerplate"
            metadata.homepage should beNull()
            metadata.projectUrls shouldBe mapOf(
                "Documentation" to "https://www.attrs.org/",
                "GitHub" to "https://github.com/python-attrs/attrs"
            )
            metadata.authorEmail shouldBe "Hynek Schlawack <hs@ox.cx>"
            metadata.licenseExpression shouldBe "MIT"
            metadata.license should beNull()
            metadata.classifiers should containExactly(
                "Development Status :: 5 - Production/Stable",
                "License :: OSI Approved :: MIT License"
            )
        }

        "join continuation lines of a header" {
            val metadata = PythonCoreMetadata.parse(
                "Name: legacy\r\n" +
                    "License: Copyright (c) 2026 Example\r\n" +
                    "        \r\n" +
                    "        Permission is hereby granted\r\n" +
                    "Home-page: https://example.org\r\n",
                source = "test"
            )

            metadata.license shouldBe "Copyright (c) 2026 Example\n        \n        Permission is hereby granted"
            metadata.homepage shouldBe "https://example.org"
        }
    }

    "getDeclaredLicenses()" should {
        "prefer the license expression over legacy fields" {
            val metadata = PythonCoreMetadata(
                license = "MIT License",
                licenseExpression = "MIT OR Apache-2.0",
                classifiers = listOf("License :: OSI Approved :: MIT License"),
                source = "test"
            )

            metadata.getDeclaredLicenses() should containExactly("MIT OR Apache-2.0")
        }

        "combine the legacy license field with license classifiers" {
            val metadata = PythonCoreMetadata(
                license = "MIT",
                classifiers = listOf(
                    "Programming Language :: Python :: 3",
                    "License :: OSI Approved :: MIT License"
                ),
                source = "test"
            )

            metadata.getDeclaredLicenses() should containExactlyInAnyOrder("MIT", "MIT License")
        }

        "not guess a license from a full license text in the legacy field" {
            val metadata = PythonCoreMetadata(
                license = "Copyright (c) 2026 Example\n\nPermission is hereby granted, free of charge, ...",
                source = "test"
            )

            metadata.getDeclaredLicenses() should beEmpty()
        }

        "ignore the placeholder value of the legacy field" {
            PythonCoreMetadata(license = "UNKNOWN", source = "test").getDeclaredLicenses() should beEmpty()
        }
    }

    "getHomepageUrl()" should {
        "prefer the legacy field over a project URL" {
            val metadata = PythonCoreMetadata(
                homepage = "https://example.org/legacy",
                projectUrls = mapOf("Homepage" to "https://example.org/modern"),
                source = "test"
            )

            metadata.getHomepageUrl() shouldBe "https://example.org/legacy"
        }

        "fall back to a project URL labeled as the homepage" {
            val metadata = PythonCoreMetadata(projectUrls = mapOf("homepage" to "https://example.org"), source = "test")

            metadata.getHomepageUrl() shouldBe "https://example.org"
        }
    }

    "getVcsFallbackUrls()" should {
        "put source code URLs first, then the homepage, then other project URLs" {
            val metadata = PythonCoreMetadata(
                homepage = "https://example.org",
                projectUrls = mapOf(
                    "Bug Tracker" to "https://github.com/example/project/issues",
                    "Changelog" to "https://github.com/example/project/blob/main/HISTORY.md",
                    "Repository" to "https://github.com/example/project"
                ),
                source = "test"
            )

            metadata.getVcsFallbackUrls() should containExactly(
                "https://github.com/example/project",
                "https://example.org",
                "https://github.com/example/project/issues",
                "https://github.com/example/project/blob/main/HISTORY.md"
            )
        }
    }

    "getAuthors()" should {
        "combine the legacy name and email fields" {
            val metadata = PythonCoreMetadata(
                author = "Armin Ronacher",
                authorEmail = "armin@example.org",
                source = "test"
            )

            metadata.getAuthors() should containExactly("Armin Ronacher <armin@example.org>")
        }

        "split several authors in the email field" {
            val metadata = PythonCoreMetadata(
                authorEmail = "Jane Doe <jane@example.org>, John Doe <john@example.org>",
                source = "test"
            )

            metadata.getAuthors() should containExactly("Jane Doe <jane@example.org>", "John Doe <john@example.org>")
        }

        "fall back to whichever field is present" {
            PythonCoreMetadata(author = "Jane Doe", source = "test").getAuthors() should containExactly("Jane Doe")
            PythonCoreMetadata(authorEmail = "jane@example.org", source = "test").getAuthors() should
                containExactly("jane@example.org")
            PythonCoreMetadata(source = "test").getAuthors() should beEmpty()
        }
    }
})
