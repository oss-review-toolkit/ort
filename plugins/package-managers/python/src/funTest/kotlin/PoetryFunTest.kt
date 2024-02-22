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

package org.ossreviewtoolkit.plugins.packagemanagers.python

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.should

import org.ossreviewtoolkit.analyzer.create
import org.ossreviewtoolkit.analyzer.resolveSingleProject
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.matchExpectedResult

class PoetryFunTest : WordSpec({
    "Python 3" should {
        "resolve dependencies correctly" {
            val definitionFile = getAssetFile("projects/synthetic/poetry/poetry.lock")
            val expectedResultFile = getAssetFile("projects/synthetic/poetry-expected-output.yml")

            val result = create("Poetry").resolveSingleProject(definitionFile)

            result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
        }

        "resolve dependencies correctly if there is no dev dependency group" {
            val definitionFile = getAssetFile("projects/synthetic/poetry-no-dev-group/poetry.lock")
            val expectedResultFile = getAssetFile("projects/synthetic/poetry-no-dev-group-expected-output.yml")

            val result = create("Poetry").resolveSingleProject(definitionFile)

            result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
        }
    }
})
