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

import com.here.ort.analyzer.managers.NPM
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.utils.normalizeVcsUrl
import com.here.ort.utils.safeDeleteRecursively
import com.here.ort.utils.yamlMapper

import io.kotlintest.TestCaseContext
import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.WordSpec

import java.io.File

class BabelTest : WordSpec() {
    private val projectDir = File("src/funTest/assets/projects/synthetic/npm-babel")
    private val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
    private val vcsUrl = vcsDir.getRemoteUrl()
    private val vcsRevision = vcsDir.getRevision()

    override fun interceptTestCase(context: TestCaseContext, test: () -> Unit) {
        try {
            super.interceptTestCase(context, test)
        } finally {
            // Make sure the node_modules directory is always deleted from each subdirectory to prevent side-effects
            // from failing tests.
            projectDir.listFiles().forEach {
                if (it.isDirectory) {
                    val nodeModulesDir = File(it, "node_modules")
                    val gitKeepFile = File(nodeModulesDir, ".gitkeep")
                    if (nodeModulesDir.isDirectory && !gitKeepFile.isFile) {
                        nodeModulesDir.safeDeleteRecursively()
                    }
                }
            }
        }
    }

    private fun patchExpectedResult(filename: String) =
            File(projectDir.parentFile, filename).readText()
                    // project.vcs_processed:
                    .replaceFirst("<REPLACE_URL>", normalizeVcsUrl(vcsUrl))
                    .replaceFirst("<REPLACE_REVISION>", vcsRevision)

    init {
        "Babel dependencies" should {
            "be correctly analyzed" {
                val npm = NPM.create()
                val packageFile = File(projectDir, "package.json")

                val expectedResult = patchExpectedResult("${projectDir.name}-expected-output.yml")
                val actualResult = npm.resolveDependencies(projectDir, listOf(packageFile))[packageFile]

                yamlMapper.writeValueAsString(actualResult) shouldBe expectedResult
            }
        }
    }
}
