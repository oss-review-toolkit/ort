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

package org.ossreviewtoolkit.utils.common

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldHaveLength

class MaskedStringTest : StringSpec({
    "CharSequence functions can be invoked" {
        val masked = MaskedString("This is a test.")

        masked shouldHaveLength MaskedString.DEFAULT_MASK.length
        masked.substring(1..3) shouldBe MaskedString.DEFAULT_MASK.substring(1..3)
    }

    "toString returns a default mask" {
        val masked = MaskedString("mySecretPassword")

        masked.toString() shouldBe MaskedString.DEFAULT_MASK
    }

    "toString returns the configured mask" {
        val mask = "???"
        val masked = MaskedString("anotherSecret", mask)

        masked.toString() shouldBe mask
    }

    "The string is masked when referenced in a join expression" {
        val strings = listOf("foo", MaskedString("bar"), "baz")

        val joinResult = strings.joinToString()

        joinResult shouldBe "foo, ${MaskedString.DEFAULT_MASK}, baz"
    }

    "Two objects can be compared with equals" {
        val masked1 = MaskedString("value", "mask")
        val masked2 = MaskedString("value", "mask")
        val masked3 = MaskedString("value1", "mask")
        val masked4 = MaskedString("value", "mask1")

        masked1 shouldBe masked2
        masked1 shouldNotBe masked3
        masked1 shouldNotBe masked4
        masked1 shouldNotBe Object()
    }

    "unmaskedStrings() returns unmasked strings" {
        val str = unmaskedStrings("foo", MaskedString("bar"), "baz").joinToString()

        str shouldBe "foo, bar, baz"
    }

    "A CharSequence can be converted to a MaskedString" {
        val value = "This is some text"
        val mask = "+++"

        val maskedValue = value.masked(mask)

        maskedValue.value shouldBe value
        maskedValue.mask shouldBe mask
    }
})
