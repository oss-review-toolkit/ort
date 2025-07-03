/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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
import io.kotest.matchers.shouldBe

class ScanCodeLicenseFactProviderTest : WordSpec({
    "removeYamlFrontMatter" should {
        "remove a YAML front matter" {
            val text = """
                ---
                key: alasir
                short_name: Alasir Licence
                name: The Alasir Licence
                category: Proprietary Free
                owner: Alasir
                homepage_url: http://alasir.com/licence/TAL.txt
                spdx_license_key: LicenseRef-scancode-alasir
                ---

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

            text.removeYamlFrontMatter() shouldBe """
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

        "remove trailing whitespace" {
            "last sentence\n".removeYamlFrontMatter() shouldBe "last sentence"
        }

        "remove leading empty lines" {
            "\nfirst sentence".removeYamlFrontMatter() shouldBe "first sentence"
        }

        "keep leading whitespace" {
            "    indented title".removeYamlFrontMatter() shouldBe "    indented title"
        }
    }
})
