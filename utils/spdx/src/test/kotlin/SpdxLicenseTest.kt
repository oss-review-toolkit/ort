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

package org.ossreviewtoolkit.utils.spdx

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith

import org.ossreviewtoolkit.utils.test.shouldNotBeNull

class SpdxLicenseTest : WordSpec({
    "The license lookup" should {
        "work by SPDX id" {
            SpdxLicense.forId("Apache-2.0") shouldBe SpdxLicense.APACHE_2_0
        }

        "work by human-readable name" {
            SpdxLicense.forId("Apache License 2.0") shouldBe SpdxLicense.APACHE_2_0
        }
    }

    "The license text" should {
        "be correct for 'or later' GPL ids" {
            val gpl10OrLater = SpdxLicense.forId("GPL-1.0+")
            gpl10OrLater shouldNotBeNull {
                val gpl10OrLaterText = text.replace("\n", " ").trimEnd()
                gpl10OrLaterText shouldStartWith "GNU GENERAL PUBLIC LICENSE  Version 1, February 1989"
                gpl10OrLaterText shouldContain "; either version 1, or (at your option) any later version."
                gpl10OrLaterText shouldEndWith "That's all there is to it!"
            }

            val gpl20OrLater = SpdxLicense.forId("GPL-2.0-or-later")
            gpl20OrLater shouldNotBeNull {
                val gpl20OrLaterText = text.replace("\n", " ").trimEnd()
                gpl20OrLaterText shouldStartWith "This program is free software; you can redistribute it and/or " +
                        "modify it under the terms of the GNU General Public License"
                gpl20OrLaterText shouldContain "; either version 2 of the License, or (at your option) any later " +
                        "version."
                gpl20OrLaterText shouldEndWith "If this is what you want to do, use the GNU Lesser General Public " +
                        "License instead of this License."
            }
        }

        "be correct for 'or later' non-GPL ids" {
            val gfdl11OrLater = SpdxLicense.forId("GFDL-1.1-or-later")
            gfdl11OrLater shouldNotBeNull {
                text shouldBe javaClass.getResource("/licenses/GFDL-1.1-or-later").readText()
            }
        }
    }
})
