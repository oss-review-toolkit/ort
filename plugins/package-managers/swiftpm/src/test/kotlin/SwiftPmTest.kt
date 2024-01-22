/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.swiftpm

import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.shouldBe

class SwiftPmTest : WordSpec({
    "getCanonicalName()" should {
        "return the expected canonical name" {
            listOf(
                "git@github.com:oss-review-toolkit/ort.git",
                "ssh://git@github.com:oss-review-toolkit/ort.git",
                "https://github.com/oss-review-toolkit/ORT.git",
                "https://github.com/oss-review-toolkit/ort/",
                "https://github.com/oss-review-toolkit/ort.git",
                "https://github.com/oss-review-toolkit/ort.git?foo=bar",
                "https://github.com/oss-review-toolkit/ort.git#bar",
                "https://github.com:1234/oss-review-toolkit/ort.git"
            ).forAll {
                getCanonicalName(it) shouldBe "github.com/oss-review-toolkit/ort"
            }
        }
    }
})
