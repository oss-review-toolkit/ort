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

package org.ossreviewtoolkit.utils.spdx

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.time.DayOfWeek

class ExtensionsTest : WordSpec({
    "EnumSet.plus" should {
        "create an empty set if both summands are empty" {
            val sum = enumSetOf<DayOfWeek>() + enumSetOf()

            sum should beEmpty()
        }

        "create the correct sum of two sets" {
            val sum = enumSetOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY) + enumSetOf(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY)

            sum shouldBe enumSetOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY)
        }
    }
})
