/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package org.ossreviewtoolkit.utils

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

class FileMatcherTest : WordSpec({
    val defaultMatcher = FileMatcher.LICENSE_FILE_MATCHER

    "default license file matcher" should {
        "match commonly used license file paths" {
            defaultMatcher.matches("LICENSE") shouldBe true
            defaultMatcher.matches("LICENSE.BSD") shouldBe true
            // TODO: add more important license file names
        }
    }

    "matches" should {
        "match the given patterns" {
            val matcher = FileMatcher("a/LICENSE", "b/LICENSE")

            matcher.matches("a/LICENSE") shouldBe true
            matcher.matches("b/LICENSE") shouldBe true
            matcher.matches("c/LICENSE") shouldBe false
        }
    }
})
