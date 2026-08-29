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

package org.ossreviewtoolkit.plugins.licensefactproviders.dir

import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.should

import org.ossreviewtoolkit.model.Identifier

class LicenseTextCurationProviderTest : WordSpec({
    "getLicenseTextForId()" should {
        val dir = tempdir().apply {
            resolve("Npm/_/mime-types.yml").apply {
                parentFile.mkdirs()
                writeText(CURATIONS_FILE_CONTENT)
            }
        }

        val provider = LicenseTextCurationProviderFactory.create(dir = dir.absolutePath)

        "return the expected texts if version matches the exact version and the version range" {
            provider.getLicenseTextsForId(
                "MIT",
                Identifier("Npm::mime-types:2.2.1")
            ).map { it.text } should containExactlyInAnyOrder(
                "Text for mime-types 2.2.1",
                "Text for mime-types from 2.0.0 up to 3.0.0."
            )
        }

        "return the expected texts if version only matches the version range" {
            provider.getLicenseTextsForId(
                "MIT",
                Identifier("Npm::mime-types:2.0.1")
            ).map { it.text } should containExactlyInAnyOrder(
                "Text for mime-types from 2.0.0 up to 3.0.0."
            )
        }

        "return no text if the version matches but not the license id" {
            provider.getLicenseTextsForId(
                "Apache-2.0",
                Identifier("Npm::mime-types:2.2.1")
            ).map { it.text } should beEmpty()
        }

        "return no text if the version does not match" {
            provider.getLicenseTextsForId(
                "MIT",
                Identifier("Npm::mime-types:10.0.0")
            ).map { it.text } should beEmpty()
        }
    }
})

private val CURATIONS_FILE_CONTENT = """
---
- id: "Npm::mime-types:2.2.1"
  curations:
  - license_id: "MIT"
    license_text: "Text for mime-types 2.2.1"
- id: "Npm::mime-types:[2.0.0,3.0.0]"
  curations:
  - license_id: "MIT"
    license_text: "Text for mime-types from 2.0.0 up to 3.0.0."
- id: "Npm::mime-types:1.0.0"
  curations:
  - license_id: "MIT"
    license_text: "Text for mime-types 1.0.0"      
""".trimIndent()
