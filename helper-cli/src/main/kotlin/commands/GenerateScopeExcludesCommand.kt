/*
 * Copyright (C) 2019 HERE Europe B.V.
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

package com.here.ort.helper.commands

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters

import com.here.ort.helper.CommandWithHelp
import com.here.ort.helper.common.minimize
import com.here.ort.helper.common.replaceScopeExcludes
import com.here.ort.helper.common.sortScopeExcludes
import com.here.ort.helper.common.writeAsYaml
import com.here.ort.model.OrtResult
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.model.config.ScopeExclude
import com.here.ort.model.config.ScopeExcludeReason
import com.here.ort.model.readValue
import com.here.ort.utils.PARAMETER_ORDER_MANDATORY
import com.here.ort.utils.PARAMETER_ORDER_OPTIONAL

import java.io.File

@Parameters(
    commandNames = ["generate-scope-excludes"],
    commandDescription = "Generate scope excludes based on common default for the package managers." +
        "The output is written to the given repository configuration file."
)
internal class GenerateScopeExcludesCommand : CommandWithHelp() {
    @Parameter(
        names = ["--ort-result-file"],
        required = true,
        order = PARAMETER_ORDER_MANDATORY
    )
    private lateinit var ortResultFile: File

    @Parameter(
        names = ["--repository-configuration-file"],
        required = true,
        order = PARAMETER_ORDER_OPTIONAL
    )
    private lateinit var repositoryConfigurationFile: File

    override fun runCommand(jc: JCommander): Int {
        val ortResult = ortResultFile.readValue<OrtResult>()
        val scopeExcludes = ortResult.generateScopeExcludes()

        repositoryConfigurationFile
            .readValue<RepositoryConfiguration>()
            .replaceScopeExcludes(scopeExcludes)
            .sortScopeExcludes()
            .writeAsYaml(repositoryConfigurationFile)

        return 0
    }
}

private fun OrtResult.generateScopeExcludes(): List<ScopeExclude> {
    val projectScopes = getProjects().flatMap { project ->
        project.scopes.map { it.name }
    }

    return getProjects().flatMap { project ->
        getScopeExcludesForPackageManager(project.id.type)
    }.minimize(projectScopes)
}

private fun getScopeExcludesForPackageManager(packageManagerName: String): List<ScopeExclude> =
    when (packageManagerName) {
        "Bower" -> listOf(
            ScopeExclude(
                name = "devDependencies",
                reason = ScopeExcludeReason.BUILD_TOOL_OF,
                comment = "Development dependencies."
            )
        )
        "Bundler" -> listOf(
            ScopeExclude(
                name = "test",
                reason = ScopeExcludeReason.TEST_TOOL_OF,
                comment = "Test dependencies."
            )
        )
        "Cargo" -> listOf(
            ScopeExclude(
                name = "build-dependencies",
                reason = ScopeExcludeReason.BUILD_TOOL_OF,
                comment = "Build dependencies."
            ),
            ScopeExclude(
                name = "dev-dependencies",
                reason = ScopeExcludeReason.BUILD_TOOL_OF,
                comment = "Development dependencies."
            )
        )
        "Gradle" -> listOf(
            ScopeExclude(
                name = "checkstyle",
                reason = ScopeExcludeReason.BUILD_TOOL_OF,
                comment = "Checkstyle dependencies."
            ),
            ScopeExclude(
                name = "detekt",
                reason = ScopeExcludeReason.BUILD_TOOL_OF,
                comment = "Detekt dependencies."
            ),
            ScopeExclude(
                name = "findbugs",
                reason = ScopeExcludeReason.BUILD_TOOL_OF,
                comment = "Findbugs dependencies."
            ),
            ScopeExclude(
                name = "jacocoAgent",
                reason = ScopeExcludeReason.BUILD_TOOL_OF,
                comment = "Test dependencies."
            ),
            ScopeExclude(
                name = "jacocoAnt",
                reason = ScopeExcludeReason.BUILD_TOOL_OF,
                comment = "Test dependencies."
            ),
            ScopeExclude(
                name = "kapt.*",
                reason = ScopeExcludeReason.PROVIDED_BY,
                comment = "Annotation processing dependencies."
            ),
            ScopeExclude(
                name = "lintClassPath",
                reason = ScopeExcludeReason.BUILD_TOOL_OF,
                comment = "Linter dependencies."
            ),
            ScopeExclude(
                name = "test.*",
                reason = ScopeExcludeReason.TEST_TOOL_OF,
                comment = "Test dependencies."
            ),
            ScopeExclude(
                name = ".*Test.*",
                reason = ScopeExcludeReason.TEST_TOOL_OF,
                comment = "Test dependencies."
            )
        )
        "Maven" -> listOf(
            ScopeExclude(
                name = "provided",
                reason = ScopeExcludeReason.PROVIDED_BY,
                comment = "Dependencies provided by the user."
            ),
            ScopeExclude(
                name = "test",
                reason = ScopeExcludeReason.TEST_TOOL_OF,
                comment = "Test dependencies."
            )
        )
        "NPM" -> listOf(
            ScopeExclude(
                name = "devDependencies",
                reason = ScopeExcludeReason.BUILD_TOOL_OF,
                comment = "Development dependencies."
            )
        )
        "PhpComposer" -> listOf(
            ScopeExclude(
                name = "require-dev",
                reason = ScopeExcludeReason.BUILD_TOOL_OF,
                comment = "Development dependencies."
            )
        )
        "SBT" -> listOf(
            ScopeExclude(
                name = "provided",
                reason = ScopeExcludeReason.PROVIDED_BY,
                comment = "Dependencies provided at runtime."
            ),
            ScopeExclude(
                name = "test",
                reason = ScopeExcludeReason.TEST_TOOL_OF,
                comment = "Test dependencies."
            )
        )
        "Stack" -> listOf(
            ScopeExclude(
                name = "bench",
                reason = ScopeExcludeReason.TEST_TOOL_OF,
                comment = "Benchmark dependencies."
            ),
            ScopeExclude(
                name = "test",
                reason = ScopeExcludeReason.TEST_TOOL_OF,
                comment = "Test dependencies."
            )
        )
        "Yarn" -> listOf(
            ScopeExclude(
                name = "devDependencies",
                reason = ScopeExcludeReason.BUILD_TOOL_OF,
                comment = "Development dependencies."
            )
        )
        else -> emptyList()
    }
