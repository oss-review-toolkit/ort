/*
 * Copyright (C) 2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.helper.commands.repoconfig

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file

import org.ossreviewtoolkit.helper.common.minimize
import org.ossreviewtoolkit.helper.common.readOrtResult
import org.ossreviewtoolkit.helper.common.replaceScopeExcludes
import org.ossreviewtoolkit.helper.common.sortScopeExcludes
import org.ossreviewtoolkit.helper.common.write
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.config.ScopeExclude
import org.ossreviewtoolkit.model.config.ScopeExcludeReason
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.utils.common.expandTilde

internal class GenerateScopeExcludesCommand : CliktCommand(
    help = "Generate scope excludes based on common default for the package managers. The output is written to the " +
            "given repository configuration file."
) {
    private val ortFile by option(
        "--ort-file", "-i",
        help = "The input ORT file from which the rule violations are read."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()

    private val repositoryConfigurationFile by option(
        "--repository-configuration-file",
        help = "Override the repository configuration contained in the given input ORT file."
    ).convert { it.expandTilde() }
        .file(mustExist = true, canBeFile = true, canBeDir = false, mustBeWritable = false, mustBeReadable = false)
        .convert { it.absoluteFile.normalize() }
        .required()

    override fun run() {
        val ortResult = readOrtResult(ortFile)
        val scopeExcludes = ortResult.generateScopeExcludes()

        repositoryConfigurationFile
            .readValue<RepositoryConfiguration>()
            .replaceScopeExcludes(scopeExcludes)
            .sortScopeExcludes()
            .write(repositoryConfigurationFile)
    }
}

private fun OrtResult.generateScopeExcludes(): List<ScopeExclude> {
    val projectScopes = getProjects().flatMap { project ->
        dependencyNavigator.scopeNames(project)
    }

    return getProjects().flatMap { project ->
        getScopeExcludesForPackageManager(project.id.type)
    }.minimize(projectScopes)
}

@Suppress("LongMethod")
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
        "Composer" -> listOf(
            ScopeExclude(
                pattern = "require-dev",
                reason = ScopeExcludeReason.DEV_DEPENDENCY_OF,
                comment = "Packages for development only."
            )
        )
        "GoMod" -> listOf(
            ScopeExclude(
                pattern = "vendor",
                reason = ScopeExcludeReason.DEV_DEPENDENCY_OF,
                comment = "Packages to build and test the main module."
            )
        )
        "Gradle" -> listOf(
            ScopeExclude(
                pattern = ".*AnnotationProcessor.*",
                reason = ScopeExcludeReason.BUILD_DEPENDENCY_OF,
                comment = "Packages to process code annotations only."
            ),
            ScopeExclude(
                pattern = "checkstyle",
                reason = ScopeExcludeReason.DEV_DEPENDENCY_OF,
                comment = "Packages for static code analysis only."
            ),
            ScopeExclude(
                pattern = "detekt",
                reason = ScopeExcludeReason.DEV_DEPENDENCY_OF,
                comment = "Packages for static code analysis (testing) only."
            ),
            ScopeExclude(
                pattern = "dokka.*",
                reason = ScopeExcludeReason.DEV_DEPENDENCY_OF,
                comment = "Packages for the Dokka documentation engine."
            ),
            ScopeExclude(
                pattern = "findbugs",
                reason = ScopeExcludeReason.DEV_DEPENDENCY_OF,
                comment = "Packages for static code analysis only."
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
                pattern = ".*JacocoAnt",
                reason = ScopeExcludeReason.TEST_DEPENDENCY_OF,
                comment = "Packages for code coverage (testing) only."
            ),
            ScopeExclude(
                pattern = "kapt.*",
                reason = ScopeExcludeReason.BUILD_DEPENDENCY_OF,
                comment = "Packages to process code annotations only."
            ),
            ScopeExclude(
                pattern = "kotlinCompiler.*",
                reason = ScopeExcludeReason.BUILD_DEPENDENCY_OF,
                comment = "Packages for Kotlin compiler only."
            ),
            ScopeExclude(
                pattern = "kotlinNativeCompilerPluginClasspath",
                reason = ScopeExcludeReason.BUILD_DEPENDENCY_OF,
                comment = "Packages for Kotlin compiler only."
            ),
            ScopeExclude(
                pattern = "ktlint",
                reason = ScopeExcludeReason.DEV_DEPENDENCY_OF,
                comment = "Packages for static code analysis only."
            ),
            ScopeExclude(
                pattern = "lint.*",
                reason = ScopeExcludeReason.DEV_DEPENDENCY_OF,
                comment = "Packages for static code analysis only."
            ),
            ScopeExclude(
                pattern = "pmd",
                reason = ScopeExcludeReason.DEV_DEPENDENCY_OF,
                comment = "Packages for static code analysis only."
            ),
            ScopeExclude(
                pattern = "spotbugs",
                reason = ScopeExcludeReason.DEV_DEPENDENCY_OF,
                comment = "Packages for static code analysis only."
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
