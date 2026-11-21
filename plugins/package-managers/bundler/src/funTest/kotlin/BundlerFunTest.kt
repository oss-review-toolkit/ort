/*
 * Copyright (C) 2017 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.bundler

import io.kotest.core.annotation.Tags
import io.kotest.core.spec.style.StringSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldBeSingle
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.haveSubstring

import org.ossreviewtoolkit.analyzer.resolveSingleProject
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.matchExpectedResult

// Requires Git to be able to use Gems from Git repositories.
@Tags("RequiresExternalTool")
class BundlerFunTest : StringSpec({
    "Resolve dependencies correctly if the project is a Gem" {
        val definitionFile = getAssetFile("projects/synthetic/gemspec/Gemfile")
        val expectedResultFile = getAssetFile("projects/synthetic/bundler-expected-output-gemspec.yml")

        val actualResult = BundlerFactory.create().resolveSingleProject(definitionFile)

        actualResult.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    withData(
        mapOf(
            "Resolve dependencies restricted to MRI for the 'x86_64-linux' platform" to
                PlatformTestCase("platforms", "x86_64-linux", setOf("nokogiri")),
            "Resolve dependencies restricted to JRuby for the 'java' platform" to
                PlatformTestCase("platforms", "java", setOf("rack")),
            "Resolve dependencies restricted to Windows for the 'x64-mingw32' platform" to
                PlatformTestCase("platforms", "x64-mingw32", setOf("rspec")),
            "Return an empty dependency graph if all dependencies are excluded" to
                PlatformTestCase("only-mri", "java", emptySet())
        )
    ) { (project, platform, expectedDependencies) ->
        val definitionFile = getAssetFile("projects/synthetic/$project/Gemfile")
        val result = BundlerFactory.create(platform = platform).resolveSingleProject(definitionFile)
        val rootDependencies = result.project.scopes.flatMap { it.dependencies }
            .mapTo(mutableSetOf()) { it.id.name }
        val resolvedDependencies = result.packages.mapTo(mutableSetOf()) { it.id.name }
            .intersect(setOf("nokogiri", "rack", "rspec"))

        result.issues should beEmpty()
        rootDependencies shouldBe expectedDependencies
        resolvedDependencies shouldBe expectedDependencies
    }

    "Resolve dependencies correctly for the default 'ruby' platform" {
        val definitionFile = getAssetFile("projects/synthetic/lockfile/Gemfile")
        val expectedResultFile = getAssetFile("projects/synthetic/bundler-expected-output-lockfile.yml")

        val actualResult = BundlerFactory.create().resolveSingleProject(definitionFile)

        actualResult.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Resolve platform-specific dependencies correctly for the 'java' platform" {
        val definitionFile = getAssetFile("projects/synthetic/lockfile/Gemfile")
        val expectedResultFile = getAssetFile("projects/synthetic/bundler-expected-output-lockfile-java.yml")

        val actualResult = BundlerFactory.create(platform = "java").resolveSingleProject(definitionFile)

        actualResult.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Resolve native Gem variants correctly for the 'x86_64-linux' platform" {
        val definitionFile = getAssetFile("projects/synthetic/native/Gemfile")
        val expectedResultFile = getAssetFile("projects/synthetic/bundler-expected-output-native-x86_64-linux.yml")

        val actualResult = BundlerFactory.create(platform = "x86_64-linux").resolveSingleProject(definitionFile)

        actualResult.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Show an error if no lockfile is present" {
        val definitionFile = getAssetFile("projects/synthetic/no-lockfile/Gemfile")
        val actualResult = BundlerFactory.create().resolveSingleProject(definitionFile)

        with(actualResult) {
            project.id shouldBe Identifier("Bundler::Gemfile:")
            project.definitionFilePath shouldBe
                "plugins/package-managers/bundler/src/funTest/assets/projects/synthetic/no-lockfile/Gemfile"
            packages should beEmpty()
            issues.shouldBeSingle().message should haveSubstring("IllegalArgumentException: No lockfile found")
        }
    }
})

private data class PlatformTestCase(val project: String, val platform: String, val expectedDependencies: Set<String>)
