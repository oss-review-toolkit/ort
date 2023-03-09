/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.utils.ort

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.utils.test.toYaml

class CopyrightStatementsProcessorTest : WordSpec({
    "process" should {
        "return a result with items merged by owner and prefix, sorted by owner and year" {
            val statements = File("src/test/assets/copyright-statements.txt").readLines()
            val expectedResult = File("src/test/assets/copyright-statements-expected-output.yml").readText()

            val actualResult = CopyrightStatementsProcessor.process(statements.shuffled()).toYaml()

            actualResult shouldBe expectedResult
        }

        "group statements with uppercase (C)" {
            val statements = listOf(
                "Copyright (C) 2017 The ORT Project Authors",
                "Copyright (C) 2022 The ORT Project Authors"
            )

            val actualResult = CopyrightStatementsProcessor.process(statements)

            actualResult.processedStatements shouldHaveSize 1
            actualResult.processedStatements.keys.first() shouldBe "Copyright (C) 2017, 2022 The ORT Project Authors"
            actualResult.unprocessedStatements should beEmpty()
        }
    }
})
