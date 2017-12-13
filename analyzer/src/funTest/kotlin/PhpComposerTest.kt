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
import com.here.ort.model.yamlMapper
import com.here.ort.utils.normalizeVcsUrl
import com.here.ort.utils.searchUpwardsForSubdirectory

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.File

class PhpComposerTest : StringSpec() {
    private val rootDir = File(".").searchUpwardsForSubdirectory(".git")!!
    private val projectDir = File(rootDir, "analyzer/src/funTest/assets/projects/synthetic/php-composer")
    private val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
    private val vcsRevision = vcsDir.getRevision()
    private val vcsUrl = vcsDir.getRemoteUrl()

    init {
        "Project dependencies are detected correctly" {
            val packageFile = File(projectDir, "composer.json")
            val result = PhpComposer.create().resolveDependencies(listOf(packageFile))[packageFile]
            val expectedResults = patchExpectedResult(File(projectDir.parentFile, "php-composer-expected-output.yml")
                    .readText())
            yamlMapper.writeValueAsString(result) shouldBe expectedResults
        }
    }

    private fun patchExpectedResult(result: String) =
            result.replaceFirst("url: \"<REPLACE_URL>\"", "url: \"${normalizeVcsUrl(vcsUrl)}\"")
                    .replaceFirst("revision: \"<REPLACE_REVISION>\"", "revision: \"$vcsRevision\"")
}
