/*
 * Copyright (c) 2017 HERE Europe B.V.
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

import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.File

private val projectDir = File("src/funTest/assets/projects/synthetic/php-composer")

class PhpComposerTest : StringSpec() {
    private val vcsDir = VersionControlSystem.forDirectory(projectDir)!!
    private val vcsRevision = vcsDir.getRevision()
    private val vcsUrl = vcsDir.getRemoteUrl()

    init {
        "Php composer recognises project" {
            val result = PackageManager.findManagedFiles(projectDir, listOf(PhpComposer))
            result[PhpComposer]?.isEmpty() shouldBe false
        }

        "Project dependencies are detected correctly" {
            val packageFile = File(projectDir, "composer.json")
            val result = PhpComposer.create().resolveDependencies(listOf(packageFile))[packageFile]
            val expectedResults = patchExpectedResult(File(projectDir.parentFile, "php-composer-expected-output.yml")
                    .readText())
            yamlMapper.writeValueAsString(result) shouldBe expectedResults
        }
    }

    private fun patchExpectedResult(result: String) =
            //vcs:
            result.replaceFirst("url: \"\"", "url: \"$vcsUrl\"")
                    .replaceFirst("revision: \"\"", "revision: \"$vcsRevision\"")
                    // vcs_processed:
                    .replaceFirst("url: \"\"", "url: \"${normalizeVcsUrl(vcsUrl)}\"")
                    .replaceFirst("revision: \"\"", "revision: \"$vcsRevision\"")
}
