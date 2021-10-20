/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.model.utils

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

class ExtensionsTest : WordSpec({
    "sanitizeMessage()" should {
        "remove additional white spaces" {
            "String with additional   white spaces. ".sanitizeMessage() shouldBe "String with additional white spaces."
        }

        "remove newlines" {
            "String\nwith\n\nnewlines.".sanitizeMessage() shouldBe "String with newlines."
        }

        "remove indentations" {
            """
                String with indentation.
            """.sanitizeMessage() shouldBe "String with indentation."
        }
    }
})
