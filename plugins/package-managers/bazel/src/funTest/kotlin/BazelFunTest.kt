/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.bazel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.analyzer.analyze
import org.ossreviewtoolkit.analyzer.create
import org.ossreviewtoolkit.analyzer.resolveSingleProject
import org.ossreviewtoolkit.model.config.PackageManagerConfiguration
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.plugins.api.PluginConfig
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.matchExpectedResult

class BazelFunTest : StringSpec({
    "Dependencies are detected correctly" {
        val definitionFile = getAssetFile("projects/synthetic/bazel/MODULE.bazel")
        val expectedResultFile = getAssetFile("projects/synthetic/bazel-expected-output.yml")

        val result = BazelFactory.create().resolveSingleProject(definitionFile)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Dependencies are detected correctly for a project with local registries" {
        val definitionFile = getAssetFile("projects/synthetic/bazel-local-registry/MODULE.bazel")
        val expectedResultFile = getAssetFile("projects/synthetic/bazel-expected-output-local-registry.yml")

        val result = BazelFactory.create().resolveSingleProject(definitionFile)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Dependencies are detected correctly for a project with local registries and Bazel >= 8.0.0" {
        val definitionFile = getAssetFile("projects/synthetic/bazel-local-registry-bazel8/MODULE.bazel")
        val expectedResultFile = getAssetFile("projects/synthetic/bazel-expected-output-local-registry-bazel8.yml")

        val result = BazelFactory.create().resolveSingleProject(definitionFile)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Dependencies are detected correctly with Bazel 7.2.0" {
        val definitionFile = getAssetFile("projects/synthetic/bazel-7.2/MODULE.bazel")
        val expectedResultFile = getAssetFile("projects/synthetic/bazel-7.2-expected-output.yml")

        val result = BazelFactory.create().resolveSingleProject(definitionFile)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Dependencies are detected correctly with Bazel 8.0.0" {
        val definitionFile = getAssetFile("projects/synthetic/bazel-8.0/MODULE.bazel")
        val expectedResultFile = getAssetFile("projects/synthetic/bazel-8.0-expected-output.yml")

        val result = BazelFactory.create().resolveSingleProject(definitionFile)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "An issue is created when the MODULE.bazel contains a different package version than the dependency graph" {
        val definitionFile = getAssetFile("projects/synthetic/bazel-8.0_warning/MODULE.bazel")

        // The warning seems outputted only once when a Bazel local server is running. Therefore, the server has to be
        // shut down before running the test.
        BazelCommand.run("shutdown", "--disk_cache=", workingDir = definitionFile.parentFile).requireSuccess()

        val result = BazelFactory.create().resolveSingleProject(definitionFile)

        result.issues shouldHaveSize 1
        result.issues.first().message shouldBe
            "WARNING: For repository 'com_google_googletest', the root module requires module version " +
            "googletest@1.14.0, but got googletest@1.14.0.bcr.1 in the resolved dependency graph. Please update the " +
            "version in your MODULE.bazel or set --check_direct_dependencies=off"
    }

    "Dependencies are detected correctly for a project with a local path override" {
        val definitionFile = getAssetFile("projects/synthetic/bazel-local-path-override/MODULE.bazel")
        val expectedResultFile = getAssetFile("projects/synthetic/bazel-expected-output-local-path-override.yml")

        val result = BazelFactory.create().resolveSingleProject(definitionFile)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Dependencies are detected correctly for a project with an archive override" {
        val definitionFile = getAssetFile("projects/synthetic/bazel-archive-override/MODULE.bazel")
        val expectedResultFile = getAssetFile("projects/synthetic/bazel-expected-output-archive-override.yml")

        val result = BazelFactory.create().resolveSingleProject(definitionFile)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Source artifact of a dependency with a 'git_repository' type is resolved correctly" {
        val definitionFile = getAssetFile("projects/synthetic/bazel-git-repository/MODULE.bazel")
        val expectedResultFile = getAssetFile("projects/synthetic/bazel-expected-output-git-repository.yml")

        val result = BazelFactory.create().resolveSingleProject(definitionFile)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Source artifact of a dependency with a 'local_path' type is resolved correctly" {
        val definitionFile = getAssetFile("projects/synthetic/bazel-local-registry-with-local-path/MODULE.bazel")
        val expectedResultFile = getAssetFile(
            "projects/synthetic/bazel-expected-output-local-registry-with-local-path.yml"
        )

        val result = BazelFactory.create().resolveSingleProject(definitionFile)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Source artifact of a dependency with a 'local_path' type and Bazel >= 8.0.0 is resolved correctly" {
        val definitionFile = getAssetFile("projects/synthetic/bazel-local-registry-with-local-path-bazel8/MODULE.bazel")
        val expectedResultFile = getAssetFile(
            "projects/synthetic/bazel-expected-output-local-registry-with-local-path-bazel8.yml"
        )

        val result = BazelFactory.create().resolveSingleProject(definitionFile)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Dependencies are detected correctly even if no lock file is present and its generation is disabled" {
        val definitionFile = getAssetFile("projects/synthetic/bazel-no-lock-file/MODULE.bazel")
        val expectedResultFile = getAssetFile(
            "projects/synthetic/bazel-expected-output-no-lock-file.yml"
        )

        val result = BazelFactory.create().resolveSingleProject(definitionFile)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Dependencies are detected correctly if the Bazel project has Conan dependencies" {
        val definitionFile = getAssetFile("projects/synthetic/bazel-conan-dependencies/MODULE.bazel")
        val expectedResultFile = getAssetFile(
            // Only "fmt" should be in the dependency tree since it is the only used Conan package.
            "projects/synthetic/bazel-expected-output-conan-dependencies.yml"
        )

        val result = create("Bazel", PluginConfig(mapOf("useConan2" to "true"))).resolveSingleProject(definitionFile)
        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Dependencies are detected if the Bazel project has Conan dependencies but only Bazel dependencies are requested" {
        val definitionFile = getAssetFile("projects/synthetic/bazel-conan-dependencies/MODULE.bazel")
        val expectedResultFile = getAssetFile(
            "projects/synthetic/bazel-expected-output-conan-dependencies-disabled.yml"
        )

        val result = create(
            "Bazel",
            PluginConfig(
                mapOf(
                    "useConan2" to "true",
                    "bazelDependenciesOnly" to "true"
                )
            )
        ).resolveSingleProject(definitionFile)
        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Bazel dependencies are detected even if the Conan package manager is disabled" {
        val definitionFile = getAssetFile("projects/synthetic/bazel-conan-dependencies/MODULE.bazel")
        val projectDir = getAssetFile("projects/synthetic/bazel-conan-dependencies/")
        val expectedResultFile = getAssetFile(
            "projects/synthetic/bazel-expected-output-conan-package-manager-disabled.yml"
        )

        val result = analyze(
            projectDir = projectDir,
            allowDynamicVersions = true,
            packageManagers = listOf(BazelFactory()),
            packageManagerConfiguration = mapOf(
                "Bazel" to PackageManagerConfiguration(
                    options = mapOf(
                        "bazelDependenciesOnly" to "true"
                    )
                )
            )
        )

        result.analyzer?.result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }
})
