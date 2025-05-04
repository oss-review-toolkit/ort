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

package org.ossreviewtoolkit.plugins.packagemanagers.composer

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.haveSubstring

import java.io.File

import org.ossreviewtoolkit.analyzer.resolveSingleProject
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.matchExpectedResult

class ComposerFunTest : StringSpec({
    "Project files from vendor directories are ignored" {
        val projectFiles = ComposerFactory.create().mapDefinitionFiles(
            File("."),
            listOf(
                "projectA/composer.json",
                "projectA/vendor/dependency1/composer.json",
                "projectB/composer.json",
                "projectB/vendor/dependency2/composer.json"
            ).map { File(it) },
            AnalyzerConfiguration()
        )

        projectFiles.map { it.path } should containExactly(
            "projectA/composer.json",
            "projectB/composer.json"
        )
    }

    "Project dependencies are detected correctly" {
        val definitionFile = getAssetFile("projects/synthetic/lockfile/composer.json")
        val expectedResultFile = getAssetFile("projects/synthetic/composer-expected-output.yml")

        val result = ComposerFactory.create().resolveSingleProject(definitionFile)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Error is shown when no lockfile is present" {
        val definitionFile = getAssetFile("projects/synthetic/no-lockfile/composer.json")
        val result = ComposerFactory.create().resolveSingleProject(definitionFile)

        with(result) {
            project.id shouldBe Identifier(
                "Composer::src/funTest/assets/projects/synthetic/no-lockfile/composer.json:"
            )
            project.definitionFilePath shouldBe "plugins/package-managers/composer/src/funTest/assets/projects/" +
                "synthetic/no-lockfile/composer.json"
            packages should beEmpty()
            issues shouldHaveSize 1
            issues.first().message should haveSubstring("IllegalArgumentException: No lockfile found in")
        }
    }

    "No composer.lock is required for projects without dependencies" {
        val definitionFile = getAssetFile("projects/synthetic/no-deps/composer.json")
        val expectedResultFile = getAssetFile("projects/synthetic/composer-expected-output-no-deps.yml")

        val result = ComposerFactory.create().resolveSingleProject(definitionFile)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "No composer.lock is required for projects with empty dependencies" {
        val definitionFile = getAssetFile("projects/synthetic/empty-deps/composer.json")
        val expectedResultFile = getAssetFile("projects/synthetic/composer-expected-output-no-deps.yml")

        val result = ComposerFactory.create().resolveSingleProject(definitionFile)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Packages defined as provided are not reported as missing" {
        val definitionFile = getAssetFile("projects/synthetic/with-provide/composer.json")
        val expectedResultFile = getAssetFile("projects/synthetic/composer-expected-output-with-provide.yml")

        val result = ComposerFactory.create().resolveSingleProject(definitionFile)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Packages defined as replaced are not reported as missing" {
        val definitionFile = getAssetFile("projects/synthetic/with-replace/composer.json")
        val expectedResultFile = getAssetFile("projects/synthetic/composer-expected-output-with-replace.yml")

        val result = ComposerFactory.create().resolveSingleProject(definitionFile)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }
})
