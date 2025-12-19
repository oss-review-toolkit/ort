/*
 * Copyright (C) 2025 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.utils.cyclonedx

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.io.File

import org.cyclonedx.exception.ParseException

import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.model.config.Includes
import org.ossreviewtoolkit.model.config.ScopeExclude
import org.ossreviewtoolkit.model.config.ScopeExcludeReason
import org.ossreviewtoolkit.model.config.ScopeInclude
import org.ossreviewtoolkit.model.config.ScopeIncludeReason
import org.ossreviewtoolkit.plugins.api.PluginDescriptor

/**
 * Test implementation of CycloneDxPackageManager for testing purposes.
 */
private class TestCycloneDxPackageManager : CycloneDxPackageManager("TestCycloneDX") {
    override val descriptor = PluginDescriptor(
        id = "TestCycloneDX",
        displayName = "Test CycloneDX",
        description = "Test implementation of CycloneDX package manager"
    )
    override val globsForDefinitionFiles = listOf("bom.cdx.json")

    override fun resolveDependencies(
        analysisRoot: File,
        definitionFile: File,
        excludes: Excludes,
        includes: Includes,
        analyzerConfig: AnalyzerConfiguration,
        labels: Map<String, String>
    ): List<ProjectAnalyzerResult> =
        resolveDependencies(
            analysisRoot,
            definitionFile,
            definitionFile.readText().toByteArray(),
            excludes,
            includes,
            analyzerConfig,
            labels
        )
}

class CycloneDxPackageManagerTest : StringSpec({
    fun manager() = TestCycloneDxPackageManager()

    "partition dependencies by scope" should {
        "handle required scope (default)" {
            val packageManager = manager()
            val bomJson = """
                {
                  "bomFormat": "CycloneDX",
                  "specVersion": "1.6",
                  "version": 1,
                  "metadata": {
                    "component": {
                      "type": "application",
                      "bom-ref": "pkg:hex/my-app@1.0.0",
                      "name": "my-app",
                      "version": "1.0.0"
                    }
                  },
                  "components": [
                    {
                      "type": "library",
                      "bom-ref": "pkg:hex/cowboy@2.9.0",
                      "name": "cowboy",
                      "version": "2.9.0",
                      "purl": "pkg:hex/cowboy@2.9.0"
                    }
                  ],
                  "dependencies": [
                    {
                      "ref": "pkg:hex/my-app@1.0.0",
                      "dependsOn": ["pkg:hex/cowboy@2.9.0"]
                    }
                  ]
                }
            """.trimIndent()

            val file = tempfile(suffix = ".json").apply {
                writeText(bomJson)
            }

            val results = packageManager.resolveDependencies(
                analysisRoot = file.parentFile,
                definitionFile = file,
                excludes = Excludes(),
                includes = Includes(),
                analyzerConfig = AnalyzerConfiguration(),
                labels = emptyMap()
            )

            results.shouldBeSingleton { result ->
                result.project.scopeNames shouldBe setOf("required")
            }
        }

        "handle optional scope" {
            val packageManager = manager()
            val bomJson = """
                {
                  "bomFormat": "CycloneDX",
                  "specVersion": "1.6",
                  "version": 1,
                  "metadata": {
                    "component": {
                      "type": "application",
                      "bom-ref": "pkg:hex/my-app@1.0.0",
                      "name": "my-app",
                      "version": "1.0.0"
                    }
                  },
                  "components": [
                    {
                      "type": "library",
                      "bom-ref": "pkg:hex/cowboy@2.9.0",
                      "name": "cowboy",
                      "version": "2.9.0",
                      "purl": "pkg:hex/cowboy@2.9.0",
                      "scope": "optional"
                    }
                  ],
                  "dependencies": [
                    {
                      "ref": "pkg:hex/my-app@1.0.0",
                      "dependsOn": ["pkg:hex/cowboy@2.9.0"]
                    }
                  ]
                }
            """.trimIndent()

            val file = tempfile(suffix = ".json").apply {
                writeText(bomJson)
            }

            val results = packageManager.resolveDependencies(
                analysisRoot = file.parentFile,
                definitionFile = file,
                excludes = Excludes(),
                includes = Includes(),
                analyzerConfig = AnalyzerConfiguration(),
                labels = emptyMap()
            )

            results.shouldBeSingleton { result ->
                result.project.scopeNames shouldBe setOf("optional")
            }
        }

        "handle excluded scope" {
            val packageManager = manager()
            val bomJson = """
                {
                  "bomFormat": "CycloneDX",
                  "specVersion": "1.6",
                  "version": 1,
                  "metadata": {
                    "component": {
                      "type": "application",
                      "bom-ref": "pkg:hex/my-app@1.0.0",
                      "name": "my-app",
                      "version": "1.0.0"
                    }
                  },
                  "components": [
                    {
                      "type": "library",
                      "bom-ref": "pkg:hex/cowboy@2.9.0",
                      "name": "cowboy",
                      "version": "2.9.0",
                      "purl": "pkg:hex/cowboy@2.9.0",
                      "scope": "excluded"
                    }
                  ],
                  "dependencies": [
                    {
                      "ref": "pkg:hex/my-app@1.0.0",
                      "dependsOn": ["pkg:hex/cowboy@2.9.0"]
                    }
                  ]
                }
            """.trimIndent()

            val file = tempfile(suffix = ".json").apply {
                writeText(bomJson)
            }

            val results = packageManager.resolveDependencies(
                analysisRoot = file.parentFile,
                definitionFile = file,
                excludes = Excludes(),
                includes = Includes(),
                analyzerConfig = AnalyzerConfiguration(),
                labels = emptyMap()
            )

            results.shouldBeSingleton { result ->
                result.project.scopeNames shouldBe setOf("excluded")
            }
        }

        "handle mixed scopes" {
            val packageManager = manager()
            val bomJson = """
                {
                  "bomFormat": "CycloneDX",
                  "specVersion": "1.6",
                  "version": 1,
                  "metadata": {
                    "component": {
                      "type": "application",
                      "bom-ref": "pkg:hex/my-app@1.0.0",
                      "name": "my-app",
                      "version": "1.0.0"
                    }
                  },
                  "components": [
                    {
                      "type": "library",
                      "bom-ref": "pkg:hex/cowboy@2.9.0",
                      "name": "cowboy",
                      "version": "2.9.0",
                      "purl": "pkg:hex/cowboy@2.9.0"
                    },
                    {
                      "type": "library",
                      "bom-ref": "pkg:hex/ranch@1.8.0",
                      "name": "ranch",
                      "version": "1.8.0",
                      "purl": "pkg:hex/ranch@1.8.0",
                      "scope": "optional"
                    },
                    {
                      "type": "library",
                      "bom-ref": "pkg:hex/mock@0.3.0",
                      "name": "mock",
                      "version": "0.3.0",
                      "purl": "pkg:hex/mock@0.3.0",
                      "scope": "excluded"
                    }
                  ],
                  "dependencies": [
                    {
                      "ref": "pkg:hex/my-app@1.0.0",
                      "dependsOn": ["pkg:hex/cowboy@2.9.0", "pkg:hex/ranch@1.8.0", "pkg:hex/mock@0.3.0"]
                    }
                  ]
                }
            """.trimIndent()

            val file = tempfile(suffix = ".json").apply {
                writeText(bomJson)
            }

            val results = packageManager.resolveDependencies(
                analysisRoot = file.parentFile,
                definitionFile = file,
                excludes = Excludes(),
                includes = Includes(),
                analyzerConfig = AnalyzerConfiguration(),
                labels = emptyMap()
            )

            results.shouldBeSingleton { result ->
                result.project.scopeNames shouldBe setOf("excluded", "optional", "required")
            }
        }

        "reject invalid uppercase scope values" {
            val packageManager = manager()
            // The CycloneDX library enforces lowercase scope values per the spec.
            // Uppercase scope values should result in a ParseException.
            val bomJson = """
                {
                  "bomFormat": "CycloneDX",
                  "specVersion": "1.6",
                  "version": 1,
                  "metadata": {
                    "component": {
                      "type": "application",
                      "bom-ref": "pkg:hex/my-app@1.0.0",
                      "name": "my-app",
                      "version": "1.0.0"
                    }
                  },
                  "components": [
                    {
                      "type": "library",
                      "bom-ref": "pkg:hex/cowboy@2.9.0",
                      "name": "cowboy",
                      "version": "2.9.0",
                      "purl": "pkg:hex/cowboy@2.9.0",
                      "scope": "OPTIONAL"
                    }
                  ],
                  "dependencies": [
                    {
                      "ref": "pkg:hex/my-app@1.0.0",
                      "dependsOn": ["pkg:hex/cowboy@2.9.0"]
                    }
                  ]
                }
            """.trimIndent()

            val file = tempfile(suffix = ".json").apply {
                writeText(bomJson)
            }

            // The CycloneDX library should reject uppercase scope values.
            shouldThrow<ParseException> {
                packageManager.resolveDependencies(
                    analysisRoot = file.parentFile,
                    definitionFile = file,
                    excludes = Excludes(),
                    includes = Includes(),
                    analyzerConfig = AnalyzerConfiguration(),
                    labels = emptyMap()
                )
            }
        }
    }

    "scope filtering" should {
        "exclude scopes matching excludes pattern" {
            val packageManager = manager()
            val bomJson = """
                {
                  "bomFormat": "CycloneDX",
                  "specVersion": "1.6",
                  "version": 1,
                  "metadata": {
                    "component": {
                      "type": "application",
                      "bom-ref": "pkg:hex/my-app@1.0.0",
                      "name": "my-app",
                      "version": "1.0.0"
                    }
                  },
                  "components": [
                    {
                      "type": "library",
                      "bom-ref": "pkg:hex/cowboy@2.9.0",
                      "name": "cowboy",
                      "version": "2.9.0",
                      "purl": "pkg:hex/cowboy@2.9.0"
                    },
                    {
                      "type": "library",
                      "bom-ref": "pkg:hex/mock@0.3.0",
                      "name": "mock",
                      "version": "0.3.0",
                      "purl": "pkg:hex/mock@0.3.0",
                      "scope": "optional"
                    }
                  ],
                  "dependencies": [
                    {
                      "ref": "pkg:hex/my-app@1.0.0",
                      "dependsOn": ["pkg:hex/cowboy@2.9.0", "pkg:hex/mock@0.3.0"]
                    }
                  ]
                }
            """.trimIndent()

            val file = tempfile(suffix = ".json").apply {
                writeText(bomJson)
            }

            val excludes = Excludes(
                scopes = listOf(
                    ScopeExclude(
                        pattern = "optional",
                        reason = ScopeExcludeReason.DEV_DEPENDENCY_OF
                    )
                )
            )

            val results = packageManager.resolveDependencies(
                analysisRoot = file.parentFile,
                definitionFile = file,
                excludes = excludes,
                includes = Includes(),
                analyzerConfig = AnalyzerConfiguration(),
                labels = emptyMap()
            )

            results.shouldBeSingleton { result ->
                result.project.scopeNames shouldBe setOf("required")
            }
        }

        "include only scopes matching includes pattern" {
            val packageManager = manager()
            val bomJson = """
                {
                  "bomFormat": "CycloneDX",
                  "specVersion": "1.6",
                  "version": 1,
                  "metadata": {
                    "component": {
                      "type": "application",
                      "bom-ref": "pkg:hex/my-app@1.0.0",
                      "name": "my-app",
                      "version": "1.0.0"
                    }
                  },
                  "components": [
                    {
                      "type": "library",
                      "bom-ref": "pkg:hex/cowboy@2.9.0",
                      "name": "cowboy",
                      "version": "2.9.0",
                      "purl": "pkg:hex/cowboy@2.9.0"
                    },
                    {
                      "type": "library",
                      "bom-ref": "pkg:hex/mock@0.3.0",
                      "name": "mock",
                      "version": "0.3.0",
                      "purl": "pkg:hex/mock@0.3.0",
                      "scope": "optional"
                    }
                  ],
                  "dependencies": [
                    {
                      "ref": "pkg:hex/my-app@1.0.0",
                      "dependsOn": ["pkg:hex/cowboy@2.9.0", "pkg:hex/mock@0.3.0"]
                    }
                  ]
                }
            """.trimIndent()

            val file = tempfile(suffix = ".json").apply {
                writeText(bomJson)
            }

            val includes = Includes(
                scopes = listOf(
                    ScopeInclude(
                        pattern = "required",
                        reason = ScopeIncludeReason.SOURCE_OF
                    )
                )
            )

            val results = packageManager.resolveDependencies(
                analysisRoot = file.parentFile,
                definitionFile = file,
                excludes = Excludes(),
                includes = includes,
                analyzerConfig = AnalyzerConfiguration(),
                labels = emptyMap()
            )

            results.shouldBeSingleton { result ->
                result.project.scopeNames shouldBe setOf("required")
            }
        }

        "excludes take precedence over includes" {
            val packageManager = manager()
            val bomJson = """
                {
                  "bomFormat": "CycloneDX",
                  "specVersion": "1.6",
                  "version": 1,
                  "metadata": {
                    "component": {
                      "type": "application",
                      "bom-ref": "pkg:hex/my-app@1.0.0",
                      "name": "my-app",
                      "version": "1.0.0"
                    }
                  },
                  "components": [
                    {
                      "type": "library",
                      "bom-ref": "pkg:hex/cowboy@2.9.0",
                      "name": "cowboy",
                      "version": "2.9.0",
                      "purl": "pkg:hex/cowboy@2.9.0"
                    },
                    {
                      "type": "library",
                      "bom-ref": "pkg:hex/mock@0.3.0",
                      "name": "mock",
                      "version": "0.3.0",
                      "purl": "pkg:hex/mock@0.3.0",
                      "scope": "optional"
                    }
                  ],
                  "dependencies": [
                    {
                      "ref": "pkg:hex/my-app@1.0.0",
                      "dependsOn": ["pkg:hex/cowboy@2.9.0", "pkg:hex/mock@0.3.0"]
                    }
                  ]
                }
            """.trimIndent()

            val file = tempfile(suffix = ".json").apply {
                writeText(bomJson)
            }

            // Include both scopes but exclude "required"
            val includes = Includes(
                scopes = listOf(
                    ScopeInclude(pattern = "required", reason = ScopeIncludeReason.SOURCE_OF),
                    ScopeInclude(pattern = "optional", reason = ScopeIncludeReason.SOURCE_OF)
                )
            )
            val excludes = Excludes(
                scopes = listOf(
                    ScopeExclude(pattern = "required", reason = ScopeExcludeReason.DEV_DEPENDENCY_OF)
                )
            )

            val results = packageManager.resolveDependencies(
                analysisRoot = file.parentFile,
                definitionFile = file,
                excludes = excludes,
                includes = includes,
                analyzerConfig = AnalyzerConfiguration(),
                labels = emptyMap()
            )

            results.shouldBeSingleton { result ->
                result.project.scopeNames shouldBe setOf("optional")
            }
        }
    }
})
