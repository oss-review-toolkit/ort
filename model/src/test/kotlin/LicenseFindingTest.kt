/*
 * Copyright (C) 2022 Bosch.IO GmbH
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
import org.ossreviewtoolkit.utils.spdx.toSpdx

class LicenseFindingTest : WordSpec({
    "createAndMap()" should {
        "apply the detected license mapping to simple SPDX expressions" {
            LicenseFinding.createAndMap(
                license = "BSD (3-Clause)",
                location = TextLocation(".", -1),
                detectedLicenseMapping = mapOf(
                    "BSD (3-Clause)" to "BSD-3-Clause",
                )
            ) shouldBe LicenseFinding(
                license = "BSD-3-Clause".toSpdx(),
                location = TextLocation(".", -1),
            )
        }

        "apply the detected license mapping if the mapping is wrapped in parentheses" {
            LicenseFinding.createAndMap(
                license = "(a)",
                location = TextLocation(".", -1),
                detectedLicenseMapping = mapOf(
                    "(a)" to "b",
                )
            ) shouldBe LicenseFinding(
                license = "b".toSpdx(),
                location = TextLocation(".", -1),
            )
        }

        "apply the detected license mapping to a valid SPDX expression" {
            LicenseFinding.createAndMap(
                license = "LicenseRef-scancode-unknown",
                location = TextLocation(".", -1),
                detectedLicenseMapping = mapOf(
                    "LicenseRef-scancode-unknown" to SpdxConstants.NOASSERTION,
                )
            ) shouldBe LicenseFinding(
                license = SpdxConstants.NOASSERTION,
                location = TextLocation(".", -1),
            )
        }

        "apply the detected license mapping only to full SPDX expressions" {
            LicenseFinding.createAndMap(
                license = "AGPL-1.0-or-later",
                location = TextLocation(".", -1),
                detectedLicenseMapping = mapOf(
                    "GPL-1.0-or-later" to "GPL-1.0-only",
                )
            ) shouldBe LicenseFinding(
                license = "AGPL-1.0-or-later".toSpdx(),
                location = TextLocation(".", -1),
            )
        }

        "apply the detected license mapping only on word boundaries" {
            LicenseFinding.createAndMap(
                license = "LicenseRef-scancode-unknown-license-reference",
                location = TextLocation(".", -1),
                detectedLicenseMapping = mapOf(
                    "LicenseRef-scancode-unknown" to SpdxConstants.NOASSERTION,
                )
            ) shouldBe LicenseFinding(
                license = "LicenseRef-scancode-unknown-license-reference".toSpdx(),
                location = TextLocation(".", -1),
            )
        }

        "apply the detected license mapping to non-parsable SPDX licenses" {
            LicenseFinding.createAndMap(
                license = "[template] Commercial License OR Commercial [template] License OR " +
                    "Commercial License [template] OR (example) test license OR " +
                    "test (example) license OR test license (example)",
                location = TextLocation(".", -1),
                detectedLicenseMapping = mapOf(
                    "[template] Commercial License" to "LicenseRef-scancode-proprietary-license",
                    "Commercial [template] License" to "LicenseRef-scancode-proprietary-license",
                    "Commercial License [template]" to "LicenseRef-scancode-proprietary-license",
                    "(example) test license" to "LicenseRef-scancode-proprietary-license",
                    "test (example) license" to "LicenseRef-scancode-proprietary-license",
                    "test license (example)" to "LicenseRef-scancode-proprietary-license",
                )
            ) shouldBe LicenseFinding(
                license = "LicenseRef-scancode-proprietary-license OR LicenseRef-scancode-proprietary-license OR " +
                    "LicenseRef-scancode-proprietary-license OR LicenseRef-scancode-proprietary-license OR " +
                    "LicenseRef-scancode-proprietary-license OR LicenseRef-scancode-proprietary-license",
                location = TextLocation(".", -1),
            )
        }

        "apply the detected license mapping to complex SPDX expressions" {
            LicenseFinding.createAndMap(
                license = "(AGPL-1.0-or-later AND BSD (3-Clause)) OR MIT",
                location = TextLocation(".", -1),
                detectedLicenseMapping = mapOf(
                    "BSD (3-Clause)" to "BSD-3-Clause",
                )
            ) shouldBe LicenseFinding(
                license = "(AGPL-1.0-or-later AND BSD-3-Clause) OR MIT".toSpdx(),
                location = TextLocation(".", -1),
            )
        }

        "apply the detected license mapping without removing necessary parentheses" {
            LicenseFinding.createAndMap(
                license = "(a OR b) AND c",
                location = TextLocation(".", -1),
                detectedLicenseMapping = mapOf(
                    "a" to "d",
                )
            ) shouldBe LicenseFinding(
                license = "(d OR b) AND c".toSpdx(),
                location = TextLocation(".", -1),
            )
        }

        "apply the detected license mapping to multiple SPDX expressions" {
            LicenseFinding.createAndMap(
                license = "AGPL-1.0-or-later OR BSD (3-Clause) OR BSD (2-Clause)",
                location = TextLocation(".", -1),
                detectedLicenseMapping = mapOf(
                    "BSD (3-Clause)" to "BSD-3-Clause",
                    "BSD (2-Clause)" to "BSD-2-Clause",
                )
            ) shouldBe LicenseFinding(
                license = "AGPL-1.0-or-later OR BSD-3-Clause OR BSD-2-Clause".toSpdx(),
                location = TextLocation(".", -1),
            )
        }
    }
})
