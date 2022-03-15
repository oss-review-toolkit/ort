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

package org.ossreviewtoolkit.utils.common

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class FileMatcherTest : StringSpec({
    "Patterns without globs should be matched" {
        val matcher = FileMatcher("a/LICENSE", "b/LICENSE")

        with(matcher) {
            matches("a/LICENSE") shouldBe true
            matches("b/LICENSE") shouldBe true
            matches("c/LICENSE") shouldBe false
        }
    }

    "Matching should adhere ignoring case" {
        FileMatcher("LICENSE", ignoreCase = false).apply {
            matches("LICENSE") shouldBe true
            matches("license") shouldBe false
        }

        FileMatcher("LICENSE", ignoreCase = true).apply {
            matches("LICENSE") shouldBe true
            matches("license") shouldBe true
        }
    }
})
