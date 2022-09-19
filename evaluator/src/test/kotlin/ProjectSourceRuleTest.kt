/*
 * Copyright (C) 2022 EPAM Systems, Inc.
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

package org.ossreviewtoolkit.evaluator

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import java.io.File
import java.time.Instant

import org.ossreviewtoolkit.model.AccessStatistics
import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.AnalyzerRun
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseFinding
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.Repository
import org.ossreviewtoolkit.model.RepositoryProvenance
import org.ossreviewtoolkit.model.ScanRecord
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScanSummary
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.ScannerRun
import org.ossreviewtoolkit.model.TextLocation
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.utils.ort.Environment
import org.ossreviewtoolkit.utils.test.createSpecTempDir

class ProjectSourceRuleTest : WordSpec({
    "projectSourceHasFile()" should {
        "return true if at least one file matches the given glob pattern" {
            val dir = createSpecTempDir().apply {
                addFiles(
                    "README.md",
                    "module/docs/LICENSE.txt"
                )
            }
            val rule = createRule(dir)

            with(rule) {
                projectSourceHasFile("README.md").matches() shouldBe true
                projectSourceHasFile("**/README.md").matches() shouldBe true
                projectSourceHasFile("**/LICENSE*").matches() shouldBe true
                projectSourceHasFile("**/*.txt").matches() shouldBe true
            }
        }

        "return false if only a directory matches the given glob pattern" {
            val dir = createSpecTempDir().apply {
                addDirs("README.md")
            }
            val rule = createRule(dir)

            rule.projectSourceHasFile("README.md").matches() shouldBe false
        }

        "return false if neither any file nor directory matches the given glob pattern" {
            val dir = createSpecTempDir()
            val rule = createRule(dir)

            rule.projectSourceHasFile("README.md").matches() shouldBe false
        }
    }

    "projectSourceHasDirectory()" should {
        "return true if at least one directory matches the given glob pattern" {
            val dir = createSpecTempDir().apply {
                addDirs("a/b/c")
            }
            val rule = createRule(dir)

            with(rule) {
                projectSourceHasDirectory("a").matches() shouldBe true
                projectSourceHasDirectory("a/b").matches() shouldBe true
                projectSourceHasDirectory("**/b/**").matches() shouldBe true
                projectSourceHasDirectory("**/c").matches() shouldBe true
            }
        }

        "return false if only a file matches the given glob pattern" {
            val dir = createSpecTempDir().apply {
                addFiles("a")
            }
            val rule = createRule(dir)

            rule.projectSourceHasDirectory("a").matches() shouldBe false
        }

        "return false if no directory matches the given glob pattern" {
            val dir = createSpecTempDir().apply {
                addDirs("b")
            }
            val rule = createRule(dir)

            rule.projectSourceHasDirectory("a").matches() shouldBe false
        }
    }

    "projectSourceHasFileWithContent()" should {
        "return true if there is a file matching the given glob pattern with its content matching the given regex" {
            val dir = createSpecTempDir().apply {
                addFiles(
                    "README.md",
                    content = """
                        
                        ## License
                    
                    """.trimIndent()
                )
            }
            val rule = createRule(dir)

            rule.projectSourceHasFileWithContent(".*^#{1,2} License$.*", "README.md").matches() shouldBe true
        }
    }

    "projectSourceGetDetectedLicensesByFilePath()" should {
        "return the detected licenses for the file matching the pattern" {
            val rule = createRule(
                createSpecTempDir(),
                ortResultWithDetectedLicenses(
                    "LICENSE" to setOf("Apache-2.0", "MIT"),
                    "README.md" to setOf("BSD-2-Clause")
                )
            )

            rule.projectSourceGetDetectedLicensesByFilePath("LICENSE") shouldBe mapOf(
                "LICENSE" to setOf("Apache-2.0", "MIT")
            )
        }
    }

    "projectSourceHasVcsType" should {
        "return true if and only if any of the given VCS types match the VCS type of the project's code repository" {
            val rule = createRule(
                createSpecTempDir(),
                createOrtResult(projectVcsType = VcsType.GIT)
            )

            rule.projectSourceHasVcsType(VcsType.GIT).matches() shouldBe true
            rule.projectSourceHasVcsType(VcsType.GIT_REPO).matches() shouldBe false
            rule.projectSourceHasVcsType(VcsType.GIT, VcsType.GIT_REPO).matches() shouldBe true
        }
    }
})

private fun createRule(projectSourcesDir: File, ortResult: OrtResult = OrtResult.EMPTY) =
    ProjectSourceRule(
        ruleSet = ruleSet(ortResult),
        name = "RULE_NAME",
        projectSourceResolver = SourceTreeResolver.forLocalDirectory(projectSourcesDir)
    )

private fun File.addFiles(vararg paths: String, content: String = "") {
    require(isDirectory)

    paths.forEach { path ->
        resolve(path).apply {
            parentFile.mkdirs()
            createNewFile()
            if (content.isNotEmpty()) writeText(content)
        }
    }
}

private fun File.addDirs(vararg paths: String) {
    require(isDirectory)

    paths.forEach { path ->
        resolve(path).mkdirs()
    }
}

private fun ortResultWithDetectedLicenses(vararg detectedLicensesForFilePath: Pair<String, Set<String>>): OrtResult =
    createOrtResult(detectedLicensesForFilePath.toMap())

private fun createOrtResult(
    detectedLicensesForFilePath: Map<String, Set<String>> = emptyMap(),
    projectVcsType: VcsType = VcsType.GIT
): OrtResult {
    val id = Identifier("Maven:org.oss-review-toolkit:example:1.0")
    val vcsInfo = VcsInfo(
        type = projectVcsType,
        url = "https://github.com/oss-review-toolkit/example.git",
        revision = "0000000000000000000000000000000000000000"
    )
    val licenseFindings = detectedLicensesForFilePath.flatMapTo(sortedSetOf()) { (filepath, licenses) ->
        licenses.map { license ->
            LicenseFinding(license, TextLocation(filepath, startLine = 1, endLine = 2))
        }
    }

    return OrtResult.EMPTY.copy(
        repository = Repository(vcsInfo),
        analyzer = AnalyzerRun(
            config = AnalyzerConfiguration(),
            environment = Environment(),
            result = AnalyzerResult.EMPTY.copy(
                projects = sortedSetOf(
                    Project.EMPTY.copy(
                        id = id,
                        vcsProcessed = vcsInfo
                    )
                )
            )
        ),
        scanner = ScannerRun.EMPTY.copy(
            results = ScanRecord(
                scanResults = sortedMapOf(
                    id to listOf(
                        ScanResult(
                            provenance = RepositoryProvenance(vcsInfo, vcsInfo.revision),
                            scanner = ScannerDetails.EMPTY,
                            summary = ScanSummary(
                                licenseFindings = licenseFindings,
                                copyrightFindings = sortedSetOf(),
                                startTime = Instant.EPOCH,
                                endTime = Instant.EPOCH,
                                packageVerificationCode = "0000000000000000000000000000000000000000"
                            )
                        )
                    )
                ),
                storageStats = AccessStatistics()
            )
        )
    )
}
