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

package org.ossreviewtoolkit.plugins.packagemanagers.python

import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should

import org.ossreviewtoolkit.analyzer.create
import org.ossreviewtoolkit.analyzer.resolveSingleProject
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.plugins.packagemanagers.python.Pip.Companion.OPTION_PYTHON_VERSION
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.matchExpectedResult

class PipFunTest : WordSpec({
    "Python 2" should {
        "resolve setup.py dependencies correctly for spdx-tools-python" {
            val definitionFile = getAssetFile("projects/external/spdx-tools-python/setup.py")
            val expectedResultFile = getAssetFile("projects/external/spdx-tools-python-expected-output.yml")

            val result = create("Pip", OPTION_PYTHON_VERSION to "2.7").resolveSingleProject(definitionFile)

            result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
        }

        "capture metadata from setup.py even if requirements.txt is present" {
            val definitionFile = getAssetFile("projects/synthetic/pip/requirements.txt")
            val expectedResultFile = getAssetFile("projects/synthetic/pip-expected-output.yml")

            val result = create("Pip", OPTION_PYTHON_VERSION to "2.7").resolveSingleProject(definitionFile)

            result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
        }
    }

    "Python 3" should {
        "resolve requirements.txt dependencies correctly for example-python-flask" {
            val definitionFile = getAssetFile("projects/external/example-python-flask/requirements.txt")

            // Note: The expected results were generated with Python 3.8 and are incorrect for versions < 3.8.
            val expectedResultFile = getAssetFile("projects/external/example-python-flask-expected-output.yml")

            val result = create("Pip", OPTION_PYTHON_VERSION to "3.10").resolveSingleProject(definitionFile)

            result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
        }

        "resolve dependencies correctly for a Django project" {
            val definitionFile = getAssetFile("projects/synthetic/pip-python3/requirements.txt")
            val expectedResultFile = getAssetFile("projects/synthetic/pip-python3-expected-output.yml")

            val result = create("Pip", OPTION_PYTHON_VERSION to "3.10").resolveSingleProject(definitionFile)

            result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
        }

        "capture metadata using python-inspector" {
            val definitionFile = getAssetFile("projects/synthetic/python-inspector/requirements.txt")
            val expectedResultFile = getAssetFile("projects/synthetic/python-inspector-expected-output.yml")

            val result = create("Pip", OPTION_PYTHON_VERSION to "3.10").resolveSingleProject(definitionFile)

            result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
        }

        "not fail if the requirements file is empty" {
            val definitionFile = tempfile("requirements", ".txt")

            val result = create("Pip", OPTION_PYTHON_VERSION to "3.10").resolveSingleProject(definitionFile)

            result.issues should beEmpty()
        }
    }
})
