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

package org.ossreviewtoolkit.evaluator.osadl

import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class CompatibilityMatrixTest : StringSpec({
    "Deserializing the matrix succeeds" {
        shouldNotThrow<IllegalArgumentException> {
            CompatibilityMatrix.releaseDateAndTime
        }
    }

    "An outbound Apache-2.0 license is incompatible with an inbound GPL-2.0-only license" {
        val info = CompatibilityMatrix.getCompatibilityInfo("Apache-2.0", "GPL-2.0-only")

        with(info) {
            compatibility shouldBe Compatibility.NO
            explanation shouldBe "Software under a copyleft license such as the GPL-2.0-only license normally cannot " +
                "be redistributed under a non-copyleft license such as the Apache-2.0 license, except if it were " +
                "explicitly permitted in the licenses."
        }
    }

    // Note: This might change due to the https://papers.ssrn.com/sol3/papers.cfm?abstract_id=4786638 paper.
    "An outbound GPL-2.0-only license is incompatible with an inbound Apache-2.0 license" {
        val info = CompatibilityMatrix.getCompatibilityInfo("GPL-2.0-only", "Apache-2.0")

        with(info) {
            compatibility shouldBe Compatibility.NO
            explanation shouldBe "Incompatibility of the Apache-2.0 license with the GPL-2.0-only license is " +
                "explicitly stated in the GPL-2.0-only license checklist."
        }
    }
})
