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

package org.ossreviewtoolkit.plugins.licensefactproviders.spdx

import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.beEmpty
import io.kotest.matchers.string.contain
import io.kotest.matchers.string.haveLength

import org.ossreviewtoolkit.utils.spdx.SpdxLicense
import org.ossreviewtoolkit.utils.spdx.SpdxLicenseException

class SpdxLicenseFactProviderTest : WordSpec({
    val provider = SpdxLicenseFactProvider()

    "getLicenseText()" should {
        "return the license text for Apache-2.0" {
            val text = provider.getLicenseText(SpdxLicense.APACHE_2_0.id)
            text should contain("Apache License")
            text should haveLength(11357)
        }

        "return the license text for all SPDX licenses" {
            enumValues<SpdxLicense>().forAll {
                provider.getLicenseText(it.id) shouldNot beEmpty()
            }
        }

        "return the license text for all SPDX exceptions" {
            enumValues<SpdxLicenseException>().forAll {
                provider.getLicenseText(it.id) shouldNot beEmpty()
            }
        }

        "return null for an unknown license" {
            provider.getLicenseText("UnknownLicense") should beNull()
        }
    }

    "hasLicenseText()" should {
        "return true for all SPDX licenses" {
            enumValues<SpdxLicense>().forAll {
                provider.hasLicenseText(it.id) shouldBe true
            }
        }

        "return true for all SPDX exceptions" {
            enumValues<SpdxLicenseException>().forAll {
                provider.hasLicenseText(it.id) shouldBe true
            }
        }

        "return false for an unknown license" {
            provider.hasLicenseText("UnknownLicense") shouldBe false
        }
    }
})
