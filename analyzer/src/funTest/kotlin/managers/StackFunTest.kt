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

package org.ossreviewtoolkit.analyzer.managers

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import org.ossreviewtoolkit.utils.test.DEFAULT_REPOSITORY_CONFIGURATION
import org.ossreviewtoolkit.utils.test.USER_DIR

class StackFunTest : StringSpec({
    "Dependencies should be resolved correctly for quickcheck-state-machine" {
        val definitionFile = projectsDir.resolve("external/quickcheck-state-machine/stack.yaml")

        val result = createStack().resolveSingleProject(definitionFile)
        val expectedOutput = if (Os.isWindows) {
            "external/quickcheck-state-machine-expected-output-win32.yml"
        } else {
            "external/quickcheck-state-machine-expected-output.yml"
        }
        val expectedResult = projectsDir.resolve(expectedOutput).readText()
        val actualResult = result.toYaml()

        actualResult shouldBe expectedResult
    }
})

private val projectsDir = File("src/funTest/assets/projects").absoluteFile

private fun createStack() =
    Stack("Stack", USER_DIR, DEFAULT_ANALYZER_CONFIGURATION, DEFAULT_REPOSITORY_CONFIGURATION)
