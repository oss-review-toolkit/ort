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

import com.here.ort.analyzer.managers.PhpComposer
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.Project
import com.here.ort.model.yamlMapper
import com.here.ort.utils.normalizeVcsUrl

import io.kotlintest.matchers.startWith
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.StringSpec

import java.io.File

class PhpComposerTest : StringSpec() {
    private val projectsDir = File("src/funTest/assets/projects/synthetic/php-composer")
    private val vcsDir = VersionControlSystem.forDirectory(projectsDir)!!
    private val vcsRevision = vcsDir.getRevision()
    private val vcsUrl = vcsDir.getRemoteUrl()

    init {
        "Project dependencies are detected correctly" {
            val definitionFile = File(projectsDir, "lockfile/composer.json")

            val result = PhpComposer.create().resolveDependencies(listOf(definitionFile))[definitionFile]
            val f = File(projectsDir.parentFile, "php-composer-expected-output.yml")
            val expectedResults = patchExpectedResult(definitionFile.parentFile,
                    f.readText())
            yamlMapper.writeValueAsString(result) shouldBe expectedResults
        }

        "Error is shown when no lock file is present" {
            val definitionFile = File(projectsDir, "no-lockfile/composer.json")

            val result = PhpComposer.create().resolveDependencies(listOf(definitionFile))[definitionFile]

            result shouldNotBe null
            result!!.project shouldBe Project.EMPTY
            result.packages.size shouldBe 0
            result.errors.size shouldBe 1
            result.errors.first() should startWith("IllegalArgumentException: No lock file found in")
        }
    }

    private fun patchExpectedResult(projectDir: File, result: String) =
            result
                    // project.vcs_processed:
                    .replaceFirst("<REPLACE_URL>", normalizeVcsUrl(vcsUrl))
                    .replaceFirst("<REPLACE_REVISION>", vcsRevision)
                    .replaceFirst("<REPLACE_PATH>", vcsDir.getPathToRoot(projectDir))
}
