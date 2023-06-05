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
    "applyDetectedLicenseMapping()" should {
        "apply the detected license mapping to simple SPDX expressions" {
            val detectedLicenseMapping = mapOf(
                "BSD (3-Clause)" to "BSD-3-Clause"
            )

            "BSD (3-Clause)".applyDetectedLicenseMapping(detectedLicenseMapping) shouldBe "BSD-3-Clause"
        }

        "apply the detected license mapping if the mapping is wrapped in parentheses" {
            val detectedLicenseMapping = mapOf(
                "(a)" to "b"
            )

            "(a)".applyDetectedLicenseMapping(detectedLicenseMapping) shouldBe "b"
        }

        "apply the detected license mapping to a valid SPDX expression" {
            val detectedLicenseMapping = mapOf(
                "LicenseRef-scancode-unknown" to SpdxConstants.NOASSERTION
            )

            "LicenseRef-scancode-unknown".applyDetectedLicenseMapping(detectedLicenseMapping) shouldBe SpdxConstants.NOASSERTION
        }

        "apply the detected license mapping only to full SPDX expressions" {
            val detectedLicenseMapping = mapOf(
                "GPL-1.0-or-later" to "GPL-1.0-only",
            )

            "AGPL-1.0-or-later".applyDetectedLicenseMapping(detectedLicenseMapping) shouldBe "AGPL-1.0-or-later"
        }

        "apply the detected license mapping only on word boundaries" {
            val detectedLicenseMapping = mapOf(
                "LicenseRef-scancode-unknown" to SpdxConstants.NOASSERTION,
            )

            "LicenseRef-scancode-unknown-license-reference".applyDetectedLicenseMapping(detectedLicenseMapping) shouldBe
                    "LicenseRef-scancode-unknown-license-reference"
        }

        "apply the detected license mapping to non-parsable SPDX licenses" {
            val detectedLicenseMapping = mapOf(
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
            ).applyDetectedLicenseMapping(detectedLicenseMapping) shouldBe
                    "LicenseRef-scancode-proprietary-license OR " +
                            "LicenseRef-scancode-proprietary-license OR " +
                            "LicenseRef-scancode-proprietary-license OR " +
                            "LicenseRef-scancode-proprietary-license OR " +
                            "LicenseRef-scancode-proprietary-license OR " +
                            "LicenseRef-scancode-proprietary-license"
        }

        "apply the detected license mapping to complex SPDX expressions" {
            val detectedLicenseMapping = mapOf(
                "BSD (3-Clause)" to "BSD-3-Clause",
            )

            "(AGPL-1.0-or-later AND BSD (3-Clause)) OR MIT".applyDetectedLicenseMapping(detectedLicenseMapping) shouldBe
                    "(AGPL-1.0-or-later AND BSD-3-Clause) OR MIT"
        }

        "apply the detected license mapping without removing necessary parentheses" {
            val detectedLicenseMapping = mapOf(
                "a" to "d",
            )

            "(a OR b) AND c".applyDetectedLicenseMapping(detectedLicenseMapping) shouldBe "(d OR b) AND c"
        }

        "apply the detected license mapping to multiple SPDX expressions" {
            val detectedLicenseMapping = mapOf(
                "BSD (3-Clause)" to "BSD-3-Clause",
                "BSD (2-Clause)" to "BSD-2-Clause",
            )

            "AGPL-1.0-or-later OR BSD (3-Clause) OR BSD (2-Clause)".applyDetectedLicenseMapping(detectedLicenseMapping) shouldBe
                    "AGPL-1.0-or-later OR BSD-3-Clause OR BSD-2-Clause"
        }
    }
})
