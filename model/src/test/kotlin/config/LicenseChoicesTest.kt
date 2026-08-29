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

package org.ossreviewtoolkit.model.config

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContainExactly

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.utils.spdxexpression.SpdxLicenseChoice
import org.ossreviewtoolkit.utils.spdxexpression.toSpdx

class LicenseChoicesTest : WordSpec({
    "merge()" should {
        "concatenate repository license choices with this taking precedence and remove duplicates" {
            val choices = LicenseChoices(
                repositoryLicenseChoices = listOf(
                    SpdxLicenseChoice("A OR B".toSpdx(), "A".toSpdx()),
                    SpdxLicenseChoice("C OR D".toSpdx(), "C".toSpdx())
                )
            )
            val otherChoices = LicenseChoices(
                repositoryLicenseChoices = listOf(
                    SpdxLicenseChoice("C OR D".toSpdx(), "C".toSpdx()),
                    SpdxLicenseChoice("E OR F".toSpdx(), "E".toSpdx())
                )
            )

            val merged = choices.merge(otherChoices)

            merged.repositoryLicenseChoices shouldContainExactly listOf(
                SpdxLicenseChoice("A OR B".toSpdx(), "A".toSpdx()),
                SpdxLicenseChoice("C OR D".toSpdx(), "C".toSpdx()),
                SpdxLicenseChoice("E OR F".toSpdx(), "E".toSpdx())
            )
        }

        "concatenate package license choices with this taking precedence and remove duplicates" {
            val id = Identifier("Maven:com.example:lib:1.0")
            val otherId = Identifier("Maven:com.example:other:1.0")

            val choices = LicenseChoices(
                packageLicenseChoices = listOf(
                    PackageLicenseChoice(id, listOf(SpdxLicenseChoice("A OR B".toSpdx(), "A".toSpdx())))
                )
            )
            val otherChoices = LicenseChoices(
                packageLicenseChoices = listOf(
                    PackageLicenseChoice(id, listOf(SpdxLicenseChoice("A OR B".toSpdx(), "A".toSpdx()))),
                    PackageLicenseChoice(id, listOf(SpdxLicenseChoice("E OR F".toSpdx(), "E".toSpdx()))),
                    PackageLicenseChoice(otherId, listOf(SpdxLicenseChoice("G OR H".toSpdx(), "G".toSpdx())))
                )
            )

            val merged = choices.merge(otherChoices)

            merged.packageLicenseChoices shouldContainExactly listOf(
                PackageLicenseChoice(id, listOf(SpdxLicenseChoice("A OR B".toSpdx(), "A".toSpdx()))),
                PackageLicenseChoice(id, listOf(SpdxLicenseChoice("E OR F".toSpdx(), "E".toSpdx()))),
                PackageLicenseChoice(otherId, listOf(SpdxLicenseChoice("G OR H".toSpdx(), "G".toSpdx())))
            )
        }
    }
})
