/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.model

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.utils.spdx.SpdxConstants

class LicenseFindingTest : WordSpec({
    "mapLicense()" should {
        "apply the license mapping to simple SPDX expressions" {
            val mapping = mapOf(
                "BSD (3-Clause)" to "BSD-3-Clause"
            )

            "BSD (3-Clause)".mapLicense(mapping) shouldBe "BSD-3-Clause"
        }

        "apply the license mapping if the mapping is wrapped in parentheses" {
            val mapping = mapOf(
                "(a)" to "b"
            )

            "(a)".mapLicense(mapping) shouldBe "b"
        }

        "apply the license mapping to a valid SPDX expression" {
            val mapping = mapOf(
                "LicenseRef-scancode-unknown" to SpdxConstants.NOASSERTION
            )

            "LicenseRef-scancode-unknown".mapLicense(mapping) shouldBe SpdxConstants.NOASSERTION
        }

        "apply the license mapping only to full SPDX expressions" {
            val mapping = mapOf(
                "GPL-1.0-or-later" to "GPL-1.0-only"
            )

            "AGPL-1.0-or-later".mapLicense(mapping) shouldBe "AGPL-1.0-or-later"
        }

        "apply the license mapping only on word boundaries" {
            val mapping = mapOf(
                "LicenseRef-scancode-unknown" to SpdxConstants.NOASSERTION
            )

            "LicenseRef-scancode-unknown-license-reference".mapLicense(mapping) shouldBe
                "LicenseRef-scancode-unknown-license-reference"
        }

        "apply the license mapping to non-parsable SPDX licenses" {
            val mapping = mapOf(
                "[template] Commercial License" to "LicenseRef-scancode-proprietary-license",
                "Commercial [template] License" to "LicenseRef-scancode-proprietary-license",
                "Commercial License [template]" to "LicenseRef-scancode-proprietary-license",
                "(example) test license" to "LicenseRef-scancode-proprietary-license",
                "test (example) license" to "LicenseRef-scancode-proprietary-license",
                "test license (example)" to "LicenseRef-scancode-proprietary-license"
            )

            (
                "[template] Commercial License OR " +
                    "Commercial [template] License OR " +
                    "Commercial License [template] OR " +
                    "(example) test license OR " +
                    "test (example) license OR " +
                    "test license (example)"
                ).mapLicense(mapping) shouldBe
                "LicenseRef-scancode-proprietary-license OR " +
                "LicenseRef-scancode-proprietary-license OR " +
                "LicenseRef-scancode-proprietary-license OR " +
                "LicenseRef-scancode-proprietary-license OR " +
                "LicenseRef-scancode-proprietary-license OR " +
                "LicenseRef-scancode-proprietary-license"
        }

        "apply the license mapping to complex SPDX expressions" {
            val mapping = mapOf(
                "BSD (3-Clause)" to "BSD-3-Clause"
            )

            "(AGPL-1.0-or-later AND BSD (3-Clause)) OR MIT".mapLicense(mapping) shouldBe
                "(AGPL-1.0-or-later AND BSD-3-Clause) OR MIT"
        }

        "apply the license mapping without removing necessary parentheses" {
            val mapping = mapOf(
                "a" to "d"
            )

            "(a OR b) AND c".mapLicense(mapping) shouldBe "(d OR b) AND c"
        }

        "apply the license mapping to multiple SPDX expressions" {
            val mapping = mapOf(
                "BSD (3-Clause)" to "BSD-3-Clause",
                "BSD (2-Clause)" to "BSD-2-Clause"
            )

            "AGPL-1.0-or-later OR BSD (3-Clause) OR BSD (2-Clause)".mapLicense(mapping) shouldBe
                "AGPL-1.0-or-later OR BSD-3-Clause OR BSD-2-Clause"
        }

        "properly replace the same license multiple times" {
            val expression = "gpl-2.0 AND (gpl-2.0 OR gpl-2.0-plus)"
            val replacements = mapOf(
                "gpl-2.0" to "GPL-2.0-only",
                "gpl-2.0-plus" to "GPL-2.0-or-later"
            )

            val result = expression.mapLicense(replacements)

            result shouldBe "GPL-2.0-only AND (GPL-2.0-only OR GPL-2.0-or-later)"
        }

        "properly handle replacements with a license being a suffix of another" {
            val expression = "agpl-3.0-openssl"
            val replacements = mapOf(
                "agpl-3.0-openssl" to "LicenseRef-scancode-agpl-3.0-openssl",
                "openssl" to "LicenseRef-scancode-openssl"
            )

            val result = expression.mapLicense(replacements)

            result shouldBe "LicenseRef-scancode-agpl-3.0-openssl"
        }
    }
})
