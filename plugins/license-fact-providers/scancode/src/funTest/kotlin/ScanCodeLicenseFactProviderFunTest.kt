/*
 * Copyright (C) 2025 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.startWith

@Tags("RequiresExternalTool")
class ScanCodeLicenseFactProviderFunTest : WordSpec({
    val provider = ScanCodeLicenseFactProviderFactory.create()

    "getLicenseText()" should {
        "return a license text for an existing license from the detected ScanCode license directory" {
            provider.getLicenseText("MIT") shouldNotBeNull {
                text should startWith("Permission is hereby granted, free of charge, to any person obtaining")
            }
        }

        "return null for an existing license which does not have a text" {
            provider.getLicenseText("LicenseRef-scancode-proprietary") shouldBe null
        }
    }

    "hasLicenseText()" should {
        "return true for an existing license" {
            provider.hasLicenseText("MIT") shouldBe true
        }

        "return false for an existing license which does not have a text" {
            provider.hasLicenseText("LicenseRef-scancode-proprietary") shouldBe false
        }
    }
})
