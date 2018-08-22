/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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

import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.yamlMapper
import com.here.ort.utils.normalizeVcsUrl
import com.here.ort.utils.test.DEFAULT_ANALYZER_CONFIGURATION
import com.here.ort.utils.test.patchActualResult
import com.here.ort.utils.test.patchExpectedResult

import io.kotlintest.shouldBe
import io.kotlintest.specs.WordSpec

import java.io.File

class ExcludesTest : WordSpec() {
    private val projectPath = File("src/funTest/assets/projects/synthetic/project-with-excludes").absoluteFile
    private val vcsDir = VersionControlSystem.forDirectory(projectPath)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()

    init {
        "Project excludes" should {
            "mark excluded components" {
                val expectedResult = patchExpectedResult(
                        File(projectPath.parentFile, "excludes-expected-result.yml"),
                        url = vcsUrl,
                        revision = vcsRevision,
                        urlProcessed = normalizeVcsUrl(vcsUrl)
                )

                val ortResult = Analyzer(DEFAULT_ANALYZER_CONFIGURATION).analyze(projectPath)

                patchActualResult(yamlMapper.writeValueAsString(ortResult)) shouldBe expectedResult
            }
        }

        "Project excludes with --remove-excludes-from-result" should {
            "remove excluded components" {
                val expectedResult = patchExpectedResult(
                        File(projectPath.parentFile, "excludes-with-remove-expected-result.yml"),
                        url = vcsUrl,
                        revision = vcsRevision,
                        urlProcessed = normalizeVcsUrl(vcsUrl)
                )

                val configWithRemove = DEFAULT_ANALYZER_CONFIGURATION.copy(removeExcludesFromResult = true)
                val ortResult = Analyzer(configWithRemove).analyze(projectPath)

                patchActualResult(yamlMapper.writeValueAsString(ortResult)) shouldBe expectedResult
            }
        }
    }
}
