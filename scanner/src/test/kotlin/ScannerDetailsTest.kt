/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package com.here.ort.scanner

import com.here.ort.model.ScannerDetails

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

class ScannerDetailsTest : StringSpec({
    val currentScannerDetails = ScannerDetails("ScanCode", "2.9.1", "")

    "Patch level releases should be compatible" {
        currentScannerDetails.isCompatible(ScannerDetails("ScanCode", "2.9.1.post7.fd2e483e3", "")) shouldBe true
        currentScannerDetails.isCompatible(ScannerDetails("ScanCode", "2.9.2", "")) shouldBe true
    }

    "Minor level releases should be not compatible" {
        currentScannerDetails.isCompatible(ScannerDetails("ScanCode", "2.8.1", "")) shouldBe false
        currentScannerDetails.isCompatible(ScannerDetails("ScanCode", "2.10.2", "")) shouldBe false
    }

    "Major level releases should be not compatible" {
        currentScannerDetails.isCompatible(ScannerDetails("ScanCode", "1.0", "")) shouldBe false
        currentScannerDetails.isCompatible(ScannerDetails("ScanCode", "3.0.0", "")) shouldBe false
    }
})
