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

package org.ossreviewtoolkit.helper.commands

import com.beust.jcommander.JCommander
import com.beust.jcommander.Parameter
import com.beust.jcommander.Parameters

import org.ossreviewtoolkit.helper.CommandWithHelp
import org.ossreviewtoolkit.helper.common.minimize
import org.ossreviewtoolkit.helper.common.replaceScopeExcludes
import org.ossreviewtoolkit.helper.common.sortScopeExcludes
import org.ossreviewtoolkit.helper.common.writeAsYaml
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.ScopeExclude
import org.ossreviewtoolkit.model.config.ScopeExcludeReason
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.utils.PARAMETER_ORDER_MANDATORY
import org.ossreviewtoolkit.utils.PARAMETER_ORDER_OPTIONAL

import java.io.File

@Parameters(
    commandNames = ["generate-scope-excludes"],
    commandDescription = "Generate scope excludes based on common default for the package managers. " +
            "The output is written to the given repository configuration file."
)
internal class GenerateScopeExcludesCommand : CommandWithHelp() {
    @Parameter(
        names = ["--ort-result-file"],
        required = true,
        order = PARAMETER_ORDER_MANDATORY,
        description = "The input ORT file from which the rule violations are read."
    )
    private lateinit var ortResultFile: File

    @Parameter(
        names = ["--repository-configuration-file"],
        required = true,
        order = PARAMETER_ORDER_OPTIONAL,
        description = "Override the repository configuration contained in the given input ORT file."
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
                pattern = "devDependencies",
                reason = ScopeExcludeReason.DEV_DEPENDENCY_OF,
                comment = "Packages for development and testing only."
            )
        )
        "Bundler" -> listOf(
            ScopeExclude(
                pattern = "test",
                reason = ScopeExcludeReason.TEST_DEPENDENCY_OF,
                comment = "Packages for testing only."
            )
        )
        "Cargo" -> listOf(
            ScopeExclude(
                pattern = "build-dependencies",
                reason = ScopeExcludeReason.BUILD_DEPENDENCY_OF,
                comment = "Packages for building the code only."
            ),
            ScopeExclude(
                pattern = "dev-dependencies",
                reason = ScopeExcludeReason.DEV_DEPENDENCY_OF,
                comment = "Packages for development only."
            )
        )
        "GoMod" -> listOf(
            ScopeExclude(
                pattern = "all",
                reason = ScopeExcludeReason.BUILD_DEPENDENCY_OF,
                comment = "Packages to build all targets including tests only."
            )
        )
        "Gradle" -> listOf(
            ScopeExclude(
                pattern = "checkstyle",
                reason = ScopeExcludeReason.BUILD_DEPENDENCY_OF,
                comment = "Packages for code styling checks (testing) only."
            ),
            ScopeExclude(
                pattern = "detekt",
                reason = ScopeExcludeReason.DEV_DEPENDENCY_OF,
                comment = "Packages for static code analysis (testing) only."
            ),
            ScopeExclude(
                pattern = "findbugs",
                reason = ScopeExcludeReason.BUILD_DEPENDENCY_OF,
                comment = "Packages for static code analysis (testing) only."
            ),
            ScopeExclude(
                pattern = "jacocoAgent",
                reason = ScopeExcludeReason.TEST_DEPENDENCY_OF,
                comment = "Packages for code coverage (testing) only."
            ),
            ScopeExclude(
                pattern = "jacocoAnt",
                reason = ScopeExcludeReason.TEST_DEPENDENCY_OF,
                comment = "Packages for code coverage (testing) only."
            ),
            ScopeExclude(
                pattern = "kapt.*",
                reason = ScopeExcludeReason.PROVIDED_DEPENDENCY_OF,
                comment = "Packages to process code annotations only."
            ),
            ScopeExclude(
                pattern = "kotlinCompiler.*",
                reason = ScopeExcludeReason.BUILD_DEPENDENCY_OF,
                comment = "Packages for Kotlin compiler only."
            ),
            ScopeExclude(
                pattern = "ktlint",
                reason = ScopeExcludeReason.TEST_DEPENDENCY_OF,
                comment = "Packages for code linting (testing) only."
            ),
            ScopeExclude(
                pattern = "lintClassPath",
                reason = ScopeExcludeReason.TEST_DEPENDENCY_OF,
                comment = "Packages for code linting (testing) only."
            ),
            ScopeExclude(
                pattern = "pmd",
                reason = ScopeExcludeReason.TEST_DEPENDENCY_OF,
                comment = "Packages for code analysis (testing) only."
            ),
            ScopeExclude(
                pattern = "test.*",
                reason = ScopeExcludeReason.TEST_DEPENDENCY_OF,
                comment = "Packages for testing only."
            ),
            ScopeExclude(
                pattern = ".*Test.*",
                reason = ScopeExcludeReason.TEST_DEPENDENCY_OF,
                comment = "Packages for testing only."
            )
        )
        "Maven" -> listOf(
            ScopeExclude(
                pattern = "provided",
                reason = ScopeExcludeReason.PROVIDED_DEPENDENCY_OF,
                comment = "Packages provided at runtime by the JDK or container only."
            ),
            ScopeExclude(
                pattern = "test",
                reason = ScopeExcludeReason.TEST_DEPENDENCY_OF,
                comment = "Packages for testing only."
            )
        )
        "NPM" -> listOf(
            ScopeExclude(
                pattern = "devDependencies",
                reason = ScopeExcludeReason.DEV_DEPENDENCY_OF,
                comment = "Packages for development only."
            )
        )
        "PhpComposer" -> listOf(
            ScopeExclude(
                pattern = "require-dev",
                reason = ScopeExcludeReason.DEV_DEPENDENCY_OF,
                comment = "Packages for development only."
            )
        )
        "SBT" -> listOf(
            ScopeExclude(
                pattern = "provided",
                reason = ScopeExcludeReason.PROVIDED_DEPENDENCY_OF,
                comment = "Packages provided at runtime by the JDK or container only."
            ),
            ScopeExclude(
                pattern = "test",
                reason = ScopeExcludeReason.TEST_DEPENDENCY_OF,
                comment = "Packages for testing only."
            )
        )
        "Stack" -> listOf(
            ScopeExclude(
                pattern = "bench",
                reason = ScopeExcludeReason.TEST_DEPENDENCY_OF,
                comment = "Packages used for benchmark testing only."
            ),
            ScopeExclude(
                pattern = "test",
                reason = ScopeExcludeReason.TEST_DEPENDENCY_OF,
                comment = "Packages for testing only."
            )
        )
        "Yarn" -> listOf(
            ScopeExclude(
                pattern = "devDependencies",
                reason = ScopeExcludeReason.DEV_DEPENDENCY_OF,
                comment = "Packages for development only."
            )
        )
        else -> emptyList()
    }
