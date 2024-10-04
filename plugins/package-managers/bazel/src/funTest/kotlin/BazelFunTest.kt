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
import io.kotest.matchers.should

import org.ossreviewtoolkit.analyzer.create
import org.ossreviewtoolkit.analyzer.resolveSingleProject
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.matchExpectedResult

class BazelFunTest : StringSpec({
    "Dependencies are detected correctly" {
        val definitionFile = getAssetFile("projects/synthetic/bazel/MODULE.bazel")
        val expectedResultFile = getAssetFile("projects/synthetic/bazel-expected-output.yml")

        val result = create("Bazel").resolveSingleProject(definitionFile)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Dependencies are detected correctly for a project with a local registries" {
        val definitionFile = getAssetFile("projects/synthetic/bazel-local-registry/MODULE.bazel")
        val expectedResultFile = getAssetFile("projects/synthetic/bazel-expected-output-local-registry.yml")

        val result = create("Bazel").resolveSingleProject(definitionFile)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Dependencies are detected correctly with Bazel 7.2.0" {
        val definitionFile = getAssetFile("projects/synthetic/bazel-7.2/MODULE.bazel")
        val expectedResultFile = getAssetFile("projects/synthetic/bazel-7.2-expected-output.yml")

        val result = create("Bazel").resolveSingleProject(definitionFile)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Dependencies are detected correctly for a project with a local path override" {
        val definitionFile = getAssetFile("projects/synthetic/bazel-local-path-override/MODULE.bazel")
        val expectedResultFile = getAssetFile("projects/synthetic/bazel-expected-output-local-path-override.yml")

        val result = create("Bazel").resolveSingleProject(definitionFile)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Dependencies are detected correctly for a project with an archive override" {
        val definitionFile = getAssetFile("projects/synthetic/bazel-archive-override/MODULE.bazel")
        val expectedResultFile = getAssetFile("projects/synthetic/bazel-expected-output-archive-override.yml")

        val result = create("Bazel").resolveSingleProject(definitionFile)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Source artifact of a dependency with a 'git_repository' type is resolved correctly" {
        val definitionFile = getAssetFile("projects/synthetic/bazel-git-repository/MODULE.bazel")
        val expectedResultFile = getAssetFile("projects/synthetic/bazel-expected-output-git-repository.yml")

        val result = create("Bazel").resolveSingleProject(definitionFile)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Source artifact of a dependency with a 'local_path' type is resolved correctly" {
        val definitionFile = getAssetFile("projects/synthetic/bazel-local-registry-with-local-path/MODULE.bazel")
        val expectedResultFile = getAssetFile(
            "projects/synthetic/bazel-expected-output-local-registry-with-local-path.yml"
        )

        val result = create("Bazel").resolveSingleProject(definitionFile)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Dependencies are detected correctly even if no lock file is present and its generation is disabled" {
        val definitionFile = getAssetFile("projects/synthetic/bazel-no-lock-file/MODULE.bazel")
        val expectedResultFile = getAssetFile(
            "projects/synthetic/bazel-expected-output-no-lock-file.yml"
        )

        val result = create("Bazel").resolveSingleProject(definitionFile)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }
})
