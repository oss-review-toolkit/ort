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

package org.ossreviewtoolkit.plugins.licensefactproviders.dir

import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

class DirLicenseFactProviderTest : WordSpec({
    "getLicenseText()" should {
        "return the license text from the configured directory" {
            val licenseDir = tempdir()
            licenseDir.resolve("LicenseRef-custom-license").writeText("custom license text")

            val provider = DirLicenseFactProviderFactory.create(licenseTextDir = licenseDir.absolutePath)

            provider.getLicenseText("LicenseRef-custom-license") shouldBe "custom license text"
        }

        "return null if no license text is found" {
            val provider = DirLicenseFactProviderFactory.create(licenseTextDir = tempdir().absolutePath)

            provider.getLicenseText("LicenseRef-non-existing-license") should beNull()
        }
    }

    "hasLicenseText()" should {
        "return true if the license text exists" {
            val licenseDir = tempdir()
            licenseDir.resolve("LicenseRef-custom-license").writeText("custom license text")

            val provider = DirLicenseFactProviderFactory.create(licenseTextDir = licenseDir.absolutePath)

            provider.hasLicenseText("LicenseRef-custom-license") shouldBe true
        }

        "return false if the license text does not exist" {
            val provider = DirLicenseFactProviderFactory.create(licenseTextDir = tempdir().absolutePath)

            provider.hasLicenseText("LicenseRef-non-existing-license") shouldBe false
        }
    }
})
