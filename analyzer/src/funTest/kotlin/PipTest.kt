/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

package com.here.ort.analyzer

import com.here.ort.analyzer.managers.PIP
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.yamlMapper
import com.here.ort.utils.normalizeVcsUrl
import com.here.ort.utils.searchUpwardsForSubdirectory

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.File

class PipTest : StringSpec({
    val rootDir = File(".").searchUpwardsForSubdirectory(".git")!!
    val projectDir = File(rootDir, "analyzer/src/funTest/assets/projects")

    "setup.py dependencies should be resolved correctly for spdx-tools-python" {
        val definitionFile = File(projectDir, "external/spdx-tools-python/setup.py")

        val result = PIP.create().resolveDependencies(listOf(definitionFile))[definitionFile]
        val expectedResult = File(projectDir, "external/spdx-tools-python-expected-output.yml").readText()

        yamlMapper.writeValueAsString(result) shouldBe expectedResult
    }

    "requirements.txt dependencies should be resolved correctly for example-python-flask" {
        val definitionFile = File(projectDir, "external/example-python-flask/requirements.txt")

        val result = PIP.create().resolveDependencies(listOf(definitionFile))[definitionFile]
        val expectedResult = File(projectDir, "external/example-python-flask-expected-output.yml").readText()

        yamlMapper.writeValueAsString(result) shouldBe expectedResult
    }

    "metadata should be captured from setup.py even if requirements.txt is present" {
        val definitionFile = File(projectDir, "synthetic/pip/requirements.txt")
        val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
        val vcsUrl = vcsDir.getRemoteUrl()
        val vcsRevision = vcsDir.getRevision()
        val vcsPath = definitionFile.parentFile.relativeTo(rootDir).invariantSeparatorsPath

        val expectedResult = File(projectDir, "synthetic/pip-expected-output.yml").readText()
                // project.vcs_processed:
                .replaceFirst("<REPLACE_URL>", normalizeVcsUrl(vcsUrl))
                .replaceFirst("<REPLACE_REVISION>", vcsRevision)
                .replaceFirst("<REPLACE_PATH>", vcsPath)

        val result = PIP.create().resolveDependencies(listOf(definitionFile))[definitionFile]

        yamlMapper.writeValueAsString(result) shouldBe expectedResult
    }
})
