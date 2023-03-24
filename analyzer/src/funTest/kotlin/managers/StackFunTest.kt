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

package org.ossreviewtoolkit.analyzer.managers

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.toYaml

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

private val projectsDir = getAssetFile("projects")

private fun createStack() =
    Stack("Stack", USER_DIR, AnalyzerConfiguration(), RepositoryConfiguration())
