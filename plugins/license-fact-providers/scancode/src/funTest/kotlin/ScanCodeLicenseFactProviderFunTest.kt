/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.startWith

import org.ossreviewtoolkit.utils.common.div

class ScanCodeLicenseFactProviderFunTest : WordSpec({
    "getLicenseText()" should {
        "read license texts from the configured directory" {
            val licenseDir = tempdir()
            (licenseDir / "test.LICENSE").writeText("custom license text")

            val provider = ScanCodeLicenseFactProviderFactory.create(scanCodeLicenseTextDir = licenseDir.absolutePath)

            provider.getLicenseText("test") shouldBe "custom license text"
        }

        "remove YAML front matter from the license text" {
            val licenseDir = tempdir()
            (licenseDir / "test.LICENSE").writeText(
                """
                |---
                |key: value
                |---
                |
                |custom license text
                """.trimMargin()
            )

            val provider = ScanCodeLicenseFactProviderFactory.create(scanCodeLicenseTextDir = licenseDir.absolutePath)

            provider.getLicenseText("test") shouldBe "custom license text"
        }

        "read license texts from the detected ScanCode license directory" {
            val provider = ScanCodeLicenseFactProviderFactory.create()

            provider.getLicenseText("MIT") shouldNotBeNull {
                this should startWith("Permission is hereby granted, free of charge, to any person obtaining")
            }
        }

        "return null for an unknown license" {
            val provider = ScanCodeLicenseFactProviderFactory.create(scanCodeLicenseTextDir = tempdir().absolutePath)

            provider.getLicenseText("UnknownLicense") should beNull()
        }
    }

    "hasLicenseText()" should {
        "return true for a known license" {
            val provider = ScanCodeLicenseFactProviderFactory.create()

            provider.hasLicenseText("MIT") shouldBe true
        }

        "return false for an unknown license" {
            val provider = ScanCodeLicenseFactProviderFactory.create(scanCodeLicenseTextDir = tempdir().absolutePath)

            provider.hasLicenseText("UnknownLicense") shouldBe false
        }
    }
})
