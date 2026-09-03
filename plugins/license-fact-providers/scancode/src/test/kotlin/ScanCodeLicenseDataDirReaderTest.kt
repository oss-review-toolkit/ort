/*
 * Copyright (C) 2023 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

import java.io.File

class ScanCodeLicenseDataDirReaderTest : WordSpec({
    val reader = ScanCodeLicenseDataDirReader(getAssetFile("license-data"))

    "getLicenseText()" should {
        "return the license text for LicenseRef-scancode-alasir" {
            val license = reader.getLicense("LicenseRef-scancode-alasir")

            license.shouldNotBeNull().text shouldBe """
                The Alasir Licence

                    This is a free software. It's provided as-is and carries absolutely no
                warranty or responsibility by the author and the contributors, neither in
                general nor in particular. No matter if this software is able or unable to
                cause any damage to your or third party's computer hardware, software, or any
                other asset available, neither the author nor a separate contributor may be
                found liable for any harm or its consequences resulting from either proper or
                improper use of the software, even if advised of the possibility of certain
                injury as such and so forth.
            """.trimIndent()
        }

        "keep leading whitespace" {
            reader.getLicense("LicenseRef-scancode-license-with-intended-title")!!.text shouldBe
                "    indented title"
        }

        "look-up a particular license for multiple SPDX license IDs defined in the same license data file" {
            val expectedText = """
                In addition to the permissions in the GNU Lesser General Public License, the
                Free Software Foundation gives you unlimited permission to link the compiled
                version of this file with other programs, and to distribute those programs
                without any restriction coming from the use of this file. 
                (The GNU Lesser General Public License restrictions do apply in other respects;
                for example, they cover modification of the file, and distribution when not
                linked into another program.)
            """.trimIndent()

            val licenseA = reader.getLicense("LicenseRef-scancode-unlimited-link-exception-lgpl")
            val licenseB = reader.getLicense("LicenseRef-scancode-unlimited-linking-exception-lgpl")

            licenseA.shouldNotBeNull().text shouldBe expectedText
            licenseB.shouldNotBeNull().text shouldBe expectedText
        }
    }
})

private fun getAssetFile(path: String) = File("src/test/assets", path).absoluteFile
