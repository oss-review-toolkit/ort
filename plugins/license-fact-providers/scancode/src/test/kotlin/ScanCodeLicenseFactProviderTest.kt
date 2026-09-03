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

package org.ossreviewtoolkit.plugins.licensefactproviders.scancode

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.io.File

class ScanCodeLicenseFactProviderTest : WordSpec({
    val provider = ScanCodeLicenseFactProviderFactory.create(
        licenseTextDir = getAssetFile("license-data").absolutePath
    )

    "getLicenseText()" should {
        "return the text for a license of the configured directory" {
            val text = provider.getLicenseText("LicenseRef-scancode-license-with-intended-title")?.text

            text shouldBe "    indented title"
        }

        "return null for a non-existing license" {
            provider.getLicenseText("LicenseRef-scancode-non-existing-license") should beNull()
        }

        "return null for an existing license without text" {
            provider.getLicenseText("LicenseRef-scancode-generic-cla") should beNull()
        }
    }

    "hasLicenseText()" should {
        "return true for a license of the configured directory" {
            provider.hasLicenseText("LicenseRef-scancode-license-with-intended-title") shouldBe true
        }

        "return false for a non-existing license" {
            provider.hasLicenseText("LicenseRef-scancode-non-existing-license") shouldBe false
        }

        "return false for an existing license without text" {
            provider.hasLicenseText("LicenseRef-scancode-generic-cla") shouldBe false
        }
    }
})

private fun getAssetFile(path: String) = File("src/test/assets", path).absoluteFile
