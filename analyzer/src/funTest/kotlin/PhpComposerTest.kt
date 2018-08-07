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

import com.here.ort.analyzer.managers.PhpComposer
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.AnalyzerConfiguration
import com.here.ort.model.Identifier
import com.here.ort.model.yamlMapper
import com.here.ort.utils.normalizeVcsUrl
import com.here.ort.utils.test.USER_DIR
import com.here.ort.utils.test.patchExpectedResult

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

            val config = AnalyzerConfiguration(false, false)
            val result = PhpComposer.create(config)
                    .resolveDependencies(USER_DIR, listOf(definitionFile))[definitionFile]
            val expectedResults = patchExpectedResult(
                    File(projectsDir.parentFile, "php-composer-expected-output.yml"),
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision,
                    path = vcsDir.getPathToRoot(definitionFile.parentFile)
            )

            yamlMapper.writeValueAsString(result) shouldBe expectedResults
        }

        "Error is shown when no lock file is present" {
            val definitionFile = File(projectsDir, "no-lockfile/composer.json")

            val config = AnalyzerConfiguration(false, false)
            val result = PhpComposer.create(config)
                    .resolveDependencies(USER_DIR, listOf(definitionFile))[definitionFile]

            result shouldNotBe null
            result!!.project.id shouldBe Identifier.fromString("PhpComposer::src/funTest/assets/projects/synthetic/" +
                    "php-composer/no-lockfile/composer.json:")
            result.project.definitionFilePath shouldBe
                    "analyzer/src/funTest/assets/projects/synthetic/php-composer/no-lockfile/composer.json"
            result.packages.size shouldBe 0
            result.errors.size shouldBe 1
            result.errors.first().message should startWith("IllegalArgumentException: No lock file found in")
        }

        "No composer.lock is required for projects without dependencies" {
            val definitionFile = File(projectsDir, "no-deps/composer.json")

            val config = AnalyzerConfiguration(false, false)
            val result = PhpComposer.create(config)
                    .resolveDependencies(USER_DIR, listOf(definitionFile))[definitionFile]
            val expectedResults = patchExpectedResult(
                    File(projectsDir.parentFile, "php-composer-expected-output-no-deps.yml"),
                    definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision,
                    path = vcsDir.getPathToRoot(definitionFile.parentFile)
            )

            yamlMapper.writeValueAsString(result) shouldBe expectedResults
        }

        "No composer.lock is required for projects with empty dependencies" {
            val definitionFile = File(projectsDir, "empty-deps/composer.json")

            val config = AnalyzerConfiguration(false, false)
            val result = PhpComposer.create(config)
                    .resolveDependencies(USER_DIR, listOf(definitionFile))[definitionFile]
            val expectedResults = patchExpectedResult(
                    File(projectsDir.parentFile, "php-composer-expected-output-no-deps.yml"),
                    definitionFilePath = VersionControlSystem.getPathInfo(definitionFile).path,
                    url = normalizeVcsUrl(vcsUrl),
                    revision = vcsRevision,
                    path = vcsDir.getPathToRoot(definitionFile.parentFile)
            )

            yamlMapper.writeValueAsString(result) shouldBe expectedResults
        }
    }
}
