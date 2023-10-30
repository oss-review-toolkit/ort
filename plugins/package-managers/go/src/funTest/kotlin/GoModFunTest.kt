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

package org.ossreviewtoolkit.plugins.packagemanagers.go

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.should

import java.io.File

import org.ossreviewtoolkit.analyzer.managers.collateMultipleProjects
import org.ossreviewtoolkit.analyzer.managers.create
import org.ossreviewtoolkit.analyzer.managers.resolveSingleProject
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.matchExpectedResult

class GoModFunTest : StringSpec({
    val testDir = getAssetFile("projects/synthetic")

    "Project dependencies are detected correctly" {
        val definitionFile = testDir.resolve("gomod/go.mod")
        val expectedResultFile = testDir.resolve("gomod-expected-output.yml")

        val result = create("GoMod").resolveSingleProject(definitionFile)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Project dependencies are detected correctly if the main package does not contain any code" {
        val definitionFile = testDir.resolve("gomod-subpkg/go.mod")
        val expectedResultFile = testDir.resolve("gomod-subpkg-expected-output.yml")

        val result = create("GoMod").resolveSingleProject(definitionFile)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Project dependencies are detected correctly if there are no dependencies" {
        val definitionFile = testDir.resolve("gomod-no-deps/go.mod")
        val expectedResultFile = testDir.resolve("gomod-no-deps-expected-output.yml")

        val result = create("GoMod").resolveSingleProject(definitionFile)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Project dependencies of a main module in a multi-module workspaces are detected correctly" {
        val definitionFile = testDir.resolve("gomod-workspaces/go.mod")
        val expectedResultFile = testDir.resolve("gomod-workspaces-main-module-expected-output.yml")

        val result = create("GoMod").resolveSingleProject(definitionFile)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Project dependencies of a submodule in a multi-module workspaces are detected correctly" {
        val definitionFile = testDir.resolve("gomod-workspaces/other-module/go.mod")
        val expectedResultFile = testDir.resolve("gomod-workspaces-sub-module-expected-output.yml")

        val result = create("GoMod").resolveSingleProject(definitionFile)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Unused dependencies are not contained in the result" {
        val definitionFile = testDir.resolve("gomod-unused-deps/go.mod")
        val expectedResultFile = testDir.resolve("gomod-unused-deps-expected-output.yml")

        val result = create("GoMod").resolveSingleProject(definitionFile)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Source files with dangling embed directives do not yield issues" {
        val definitionFile = testDir.resolve("gomod-dangling-embed/go.mod")
        val expectedResultFile = testDir.resolve("gomod-dangling-embed-expected-output.yml")

        val result = create("GoMod").resolveSingleProject(definitionFile)

        result.toYaml() should matchExpectedResult(expectedResultFile, definitionFile)
    }

    "Project dependencies with a (relative) local module dependency are detected correctly" {
        val definitionFileApp = testDir.resolve("gomod-submodules/app/go.mod")
        val definitionFileUtils = testDir.resolve("gomod-submodules/utils/go.mod")
        val expectedResultFile = testDir.resolve("gomod-submodules-expected-output.yml")
        val expectedDefinitionFilePathUtils = getDefinitionFilePath(definitionFileUtils)

        val result = create("GoMod").collateMultipleProjects(definitionFileApp, definitionFileUtils)

        result.withResolvedScopes().toYaml() should matchExpectedResult(
            expectedResultFile,
            definitionFileApp,
            custom = mapOf(
                "<REPLACE_DEFINITION_FILE_PATH_UTILS>" to expectedDefinitionFilePathUtils,
                "<REPLACE_PATH_UTILS>" to expectedDefinitionFilePathUtils.substringBeforeLast('/')
            )
        )
    }
})

private fun getDefinitionFilePath(definitionFile: File): String {
    val projectDir = definitionFile.parentFile
    val vcsDir = checkNotNull(VersionControlSystem.forDirectory(projectDir))
    val path = vcsDir.getPathToRoot(projectDir)
    return "$path/${definitionFile.name}"
}
