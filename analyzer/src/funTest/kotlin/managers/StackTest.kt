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

package org.ossreviewtoolkit.analyzer.managers

import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.utils.Os
import org.ossreviewtoolkit.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import org.ossreviewtoolkit.utils.test.DEFAULT_REPOSITORY_CONFIGURATION
import org.ossreviewtoolkit.utils.test.USER_DIR

import io.kotest.matchers.shouldBe
import io.kotest.core.spec.style.StringSpec

import java.io.File

class StackTest : StringSpec() {
    private val projectsDir = File("src/funTest/assets/projects").absoluteFile

    init {
        "Dependencies should be resolved correctly for quickcheck-state-machine" {
            val definitionFile = File(projectsDir, "external/quickcheck-state-machine/stack.yaml")

            val result = createStack().resolveSingleProject(definitionFile)
            val expectedOutput = if (Os.isWindows) {
                "external/quickcheck-state-machine-expected-output-win32.yml"
            } else {
                "external/quickcheck-state-machine-expected-output.yml"
            }
            val expectedResult = File(projectsDir, expectedOutput).readText()
            val actualResult = yamlMapper.writeValueAsString(result)

            actualResult shouldBe expectedResult
        }
    }

    private fun createStack() =
        Stack("Stack", USER_DIR, DEFAULT_ANALYZER_CONFIGURATION, DEFAULT_REPOSITORY_CONFIGURATION)
}
