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

package org.ossreviewtoolkit.analyzer.managers

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import io.mockk.EqMatcher
import io.mockk.VarargMatcher
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor

import java.io.File

import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.PackageManagerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.utils.common.ProcessCapture
import org.ossreviewtoolkit.utils.test.createTestTempDir

class NpmTest : StringSpec({
    "The output of the npm command should be parsed correctly" {
        val workingDir = createTestTempDir()
        val definitionFileSrc = File("src/test/assets/test-package-no-deps.json")
        val definitionFile = workingDir.resolve("package.json")
        definitionFileSrc.copyTo(definitionFile)

        val errorFile = File("src/test/assets/npm-err.txt")
        val errorText = errorFile.readText()

        mockkConstructor(ProcessCapture::class)
        try {
            every {
                constructedWith<ProcessCapture>(
                    EqMatcher(workingDir),
                    VarargMatcher(
                        all = true,
                        matcher = { true },
                        prefix = listOf(
                            EqMatcher("npm"),
                            EqMatcher("install"),
                            EqMatcher("--ignore-scripts"),
                            EqMatcher("--no-audit"),
                            EqMatcher("--legacy-peer-deps")
                        )
                    )
                ).stderr
            } returns errorText

            val analyzerName = "test-npm"
            val npmOptions = mapOf("legacyPeerDeps" to "true")
            val npmConfig = PackageManagerConfiguration(options = npmOptions)
            val analyzerConfig =
                AnalyzerConfiguration(allowDynamicVersions = true, packageManagers = mapOf(analyzerName to npmConfig))
            val npm = Npm(analyzerName, workingDir, analyzerConfig, RepositoryConfiguration())

            val results = npm.resolveDependencies(definitionFile, emptyMap())

            results shouldHaveSize 1

            with(results[0]) {
                packages should beEmpty()
                issues shouldHaveSize 1
                issues[0].severity shouldBe Severity.ERROR
            }
        } finally {
            unmockkConstructor(ProcessCapture::class)
        }
    }
})
