/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.scanner

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.ScannerDetails

private val scanCodeDetails = ScannerDetails("ScanCode", "2.9.1", "")

class ScannerDetailsTest : WordSpec({
    "The same scanner" should {
        "be compatible to itself" {
            scanCodeDetails.isCompatible(scanCodeDetails) shouldBe true
        }

        "be compatible if the name differs in case" {
            val detailsLowerCaseName = scanCodeDetails.copy(name = scanCodeDetails.name.lowercase())
            val detailsUpperCaseName = scanCodeDetails.copy(name = scanCodeDetails.name.uppercase())
            detailsLowerCaseName.isCompatible(detailsUpperCaseName) shouldBe true
        }

        "be compatible if only the pre-release / build identifier differs" {
            scanCodeDetails.isCompatible(ScannerDetails("ScanCode", "2.9.1-rc2", "")) shouldBe true
            scanCodeDetails.isCompatible(ScannerDetails("ScanCode", "2.9.1+build", "")) shouldBe true
        }

        "be compatible if only the patch level differs" {
            scanCodeDetails.isCompatible(ScannerDetails("ScanCode", "2.9.1.post7.fd2e483e3", "")) shouldBe true
            scanCodeDetails.isCompatible(ScannerDetails("ScanCode", "2.9.2", "")) shouldBe true
        }

        "not be compatible if the minor level differs" {
            scanCodeDetails.isCompatible(ScannerDetails("ScanCode", "2.8.1", "")) shouldBe false
            scanCodeDetails.isCompatible(ScannerDetails("ScanCode", "2.10.2", "")) shouldBe false
        }

        "not be compatible if the major level differs" {
            scanCodeDetails.isCompatible(ScannerDetails("ScanCode", "1.0", "")) shouldBe false
            scanCodeDetails.isCompatible(ScannerDetails("ScanCode", "3.0.0", "")) shouldBe false
        }

        "not be compatible if the configuration differs" {
            scanCodeDetails.isCompatible(scanCodeDetails.copy(configuration = "--foo-bar-option")) shouldBe false
        }
    }

    "Different scanners" should {
        "never be compatible" {
            scanCodeDetails.isCompatible(scanCodeDetails.copy(name = "Other")) shouldBe false
        }
    }
})
