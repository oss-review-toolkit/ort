/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.nuget

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.analyzer.managers.create
import org.ossreviewtoolkit.analyzer.managers.resolveSingleProject
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.patchExpectedResult

class NuGetFunTest : StringSpec({
    "DotNet project dependencies are detected correctly" {
        val definitionFile = getAssetFile("dotnet/subProjectTest/test.csproj")
        val expectedResultFile = getAssetFile("dotnet-expected-output.yml")

        val result = create("NuGet").resolveSingleProject(definitionFile)

        result.toYaml() shouldBe patchExpectedResult(expectedResultFile, definitionFile)
    }

    "License extraction is done correctly" {
        val definitionFile = getAssetFile("dotnet/subProjectTestWithCsProj/test.csproj")
        val expectedResultFile = getAssetFile("dotnet-license-data-extraction.yml")

        val result = create("NuGet").resolveSingleProject(definitionFile)

        result.toYaml() shouldBe patchExpectedResult(expectedResultFile, definitionFile)
    }

    "A .csproj file with an accompanying .nuspec file is detected correctly" {
        val definitionFile = getAssetFile("dotnet/subProjectTestWithNuspec/test.csproj")
        val expectedResultFile = getAssetFile("dotnet-expected-output-with-nuspec.yml")

        val result = create("NuGet").resolveSingleProject(definitionFile)

        result.toYaml() shouldBe patchExpectedResult(expectedResultFile, definitionFile)
    }

    "A large number of dependencies is resolved at once in a .csproj file" {
        val definitionFile = getAssetFile("dotnet/subProjectTestWithManyDepsCsProj/test.csproj")
        val expectedResultFile = getAssetFile("dotnet-many-deps-resolution.yml")

        val result = create("NuGet").resolveSingleProject(definitionFile)

        result.toYaml() shouldBe patchExpectedResult(expectedResultFile, definitionFile)
    }

    "NuGet project dependencies are detected correctly" {
        val definitionFile = getAssetFile("nuget/packages.config")
        val expectedResultFile = getAssetFile("nuget-expected-output.yml")

        val result = create("NuGet").resolveSingleProject(definitionFile)

        result.toYaml() shouldBe patchExpectedResult(expectedResultFile, definitionFile)
    }

    "Project dependencies are detected correctly with a nuget.config present" {
        val definitionFile = getAssetFile("dotnet/subProjectTestWithNugetConfig/test.csproj")
        val expectedResultFile = getAssetFile("dotnet-with-csproj-and-nuget-config-output.yml")

        val result = create(
            "NuGet",
            "nugetConfigFile" to getAssetFile(
                "dotnet/subProjectTestWithNugetConfig/NugetConfig/nuget.config"
            ).absolutePath
        ).resolveSingleProject(definitionFile)

        result.toYaml() shouldBe patchExpectedResult(expectedResultFile, definitionFile)
    }
})
