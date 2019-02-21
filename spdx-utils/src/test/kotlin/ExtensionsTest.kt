/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package com.here.ort.spdx

import io.kotlintest.assertSoftly
import io.kotlintest.matchers.beEmpty
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

import java.time.DayOfWeek

class ExtensionsTest : WordSpec({
    "EnumSet.plus" should {
        "create an empty set if both summands are empty" {
            val sum = enumSetOf<DayOfWeek>() + enumSetOf()

            sum should beEmpty()
        }

        "create the correct sum of two sets" {
            val sum = enumSetOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY) + enumSetOf(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY)

            sum shouldBe enumSetOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY)
        }
    }

    "String.isLicenseRefTo" should {
        "return true if the string is a LicenseRef" {
            assertSoftly {
                "LicenseRef-proprietary".isLicenseRefTo("proprietary") shouldBe true
                "LicenseRef-scancode-public-domain".isLicenseRefTo("public-domain") shouldBe true
                "LicenseRef-ScanCode-Public-Domain".isLicenseRefTo("public-domain") shouldBe true
            }
        }

        "return false if the string is not a LicenseRef" {
            assertSoftly {
                "LicenseRef".isLicenseRefTo("") shouldBe false
                "LicenseRef-".isLicenseRefTo("") shouldBe false
                "LicenseRef--".isLicenseRefTo("-") shouldBe false
                "LicenseRef--foo".isLicenseRefTo("-foo") shouldBe false
                "LicenseRef--foo".isLicenseRefTo("foo") shouldBe false
                "public-domain".isLicenseRefTo("public-domain") shouldBe false
                "".isLicenseRefTo("") shouldBe false
                "-".isLicenseRefTo("public-domain") shouldBe false
            }
        }

        "return false if the namespace is not known" {
            "LicenseRef-no-public-domain".isLicenseRefTo("public-domain") shouldBe false
        }
    }
})
