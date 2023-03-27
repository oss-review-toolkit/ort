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
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.analyzer.managers.resolveSingleProject
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.PackageManagerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.test.USER_DIR
import org.ossreviewtoolkit.utils.test.createTestTempFile
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.patchExpectedResult2
import org.ossreviewtoolkit.utils.test.toYaml

class PipFunTest : WordSpec({
    "Python 2" should {
        "resolve setup.py dependencies correctly for spdx-tools-python" {
            val definitionFile = getAssetFile("projects/external/spdx-tools-python/setup.py")
            val expectedResultFile = getAssetFile("projects/external/spdx-tools-python-expected-output.yml")

            val result = createPip(pythonVersion = "2.7").resolveSingleProject(definitionFile)

            result.toYaml() shouldBe patchExpectedResult2(expectedResultFile, definitionFile)
        }

        "capture metadata from setup.py even if requirements.txt is present" {
            val definitionFile = getAssetFile("projects/synthetic/pip/requirements.txt")
            val expectedResultFile = getAssetFile("projects/synthetic/pip-expected-output.yml")

            val result = createPip(pythonVersion = "2.7").resolveSingleProject(definitionFile)

            result.toYaml() shouldBe patchExpectedResult2(expectedResultFile, definitionFile)
        }
    }

    "Python 3" should {
        "resolve requirements.txt dependencies correctly for example-python-flask" {
            val definitionFile = getAssetFile("projects/external/example-python-flask/requirements.txt")

            // Note: The expected results were generated with Python 3.8 and are incorrect for versions < 3.8.
            val suffix = "-windows".takeIf { Os.isWindows }.orEmpty()
            val expectedResultFile = getAssetFile("projects/external/example-python-flask-expected-output$suffix.yml")

            val result = createPip().resolveSingleProject(definitionFile)

            result.toYaml() shouldBe patchExpectedResult2(expectedResultFile, definitionFile)
        }

        "resolve dependencies correctly for a Django project" {
            val definitionFile = getAssetFile("projects/synthetic/pip-python3/requirements.txt")
            val expectedResultFile = getAssetFile("projects/synthetic/pip-python3-expected-output.yml")

            val result = createPip().resolveSingleProject(definitionFile)

            result.toYaml() shouldBe patchExpectedResult2(expectedResultFile, definitionFile)
        }

        "capture metadata using python-inspector" {
            val definitionFile = getAssetFile("projects/synthetic/python-inspector/requirements.txt")
            val expectedResultFile = getAssetFile("projects/synthetic/python-inspector-expected-output.yml")

            val result = createPip().resolveSingleProject(definitionFile)

            result.toYaml() shouldBe patchExpectedResult2(expectedResultFile, definitionFile)
        }

        "not fail if the requirements file is empty" {
            val definitionFile = createTestTempFile(prefix = "requirements", suffix = ".txt")

            val result = createPip().resolveSingleProject(definitionFile)

            result.issues should beEmpty()
        }
    }
})

private fun createPip(pythonVersion: String = "3.10") =
    Pip("PIP", USER_DIR, createAnalyzerConfiguration(pythonVersion), RepositoryConfiguration())

private fun createAnalyzerConfiguration(pythonVersion: String) =
    AnalyzerConfiguration(
        packageManagers = mapOf(
            Pip.Factory().type to PackageManagerConfiguration(
                options = mapOf("pythonVersion" to pythonVersion)
            )
        )
    )
