/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.spdx

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.haveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.analyzer.managers.create
import org.ossreviewtoolkit.analyzer.managers.resolveSingleProject
import org.ossreviewtoolkit.downloader.VersionControlSystem
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.utils.ort.normalizeVcsUrl
import org.ossreviewtoolkit.utils.test.getAssetFile
import org.ossreviewtoolkit.utils.test.matchExpectedResult
import org.ossreviewtoolkit.utils.test.shouldNotBeNull

class SpdxDocumentFileFunTest : WordSpec({
    "resolveDependencies()" should {
        "succeed if a project with inline packages is provided" {
            val definitionFile = getAssetFile("projects/synthetic/inline-packages/project-xyz.spdx.yml")
            val expectedResultFile = getAssetFile("projects/synthetic/spdx-project-xyz-expected-output.yml")

            val actualResult = create("SpdxDocumentFile").resolveSingleProject(definitionFile).toYaml()

            actualResult should matchExpectedResult(expectedResultFile, definitionFile)
        }

        "succeed if a project with package references is provided" {
            val definitionFile = getAssetFile("projects/synthetic/package-references/project-xyz.spdx.yml")
            val expectedResultFile = getAssetFile("projects/synthetic/spdx-project-xyz-expected-output.yml")

            val actualResult = create("SpdxDocumentFile").resolveSingleProject(definitionFile).toYaml()

            actualResult should matchExpectedResult(expectedResultFile, definitionFile)
        }

        "succeed if no project is provided" {
            val curlPackageFile = projectDir.resolve("libs/curl/package.spdx.yml")
            val curlId = Identifier("SpdxDocumentFile::curl:7.70.0")

            val opensslPackageFile = projectDir.resolve("libs/openssl/package.spdx.yml")
            val opensslId = Identifier("SpdxDocumentFile:OpenSSL Development Team:openssl:1.1.1g")

            val zlibPackageFile = projectDir.resolve("libs/zlib/package.spdx.yml")
            val zlibId = Identifier("SpdxDocumentFile::zlib:1.2.11")

            val definitionFiles = listOf(curlPackageFile, opensslPackageFile, zlibPackageFile)
            val actualResult = create("SpdxDocumentFile").resolveDependencies(definitionFiles, emptyMap())
                // Extract only ProjectAnalyzerResults to avoid depending on other analyzer result specific items (e.g.
                // the dependency graph).
                .projectResults.values.flatten().associateBy { it.project.id }

            actualResult should haveSize(3)

            actualResult[curlId] shouldBe ProjectAnalyzerResult(
                Project(
                    id = curlId,
                    cpe = "cpe:2.3:a:http:curl:7.70.0:*:*:*:*:*:*:*",
                    definitionFilePath = vcsDir.getPathToRoot(curlPackageFile),
                    authors = setOf("Daniel Stenberg (daniel@haxx.se)"),
                    declaredLicenses = setOf("curl"),
                    vcs = VcsInfo(
                        type = VcsType.GIT,
                        url = normalizeVcsUrl(vcsUrl),
                        revision = vcsRevision,
                        path = vcsDir.getPathToRoot(curlPackageFile.parentFile)
                    ),
                    homepageUrl = "https://curl.haxx.se/",
                    scopeDependencies = setOf(
                        Scope("default")
                    )
                ),
                emptySet()
            )

            actualResult[opensslId] shouldBe ProjectAnalyzerResult(
                Project(
                    id = opensslId,
                    cpe = "cpe:2.3:a:a-name:openssl:1.1.1g:*:*:*:*:*:*:*",
                    definitionFilePath = vcsDir.getPathToRoot(opensslPackageFile),
                    authors = setOf("OpenSSL Development Team"),
                    declaredLicenses = setOf("Apache-2.0"),
                    vcs = VcsInfo(
                        type = VcsType.GIT,
                        url = normalizeVcsUrl(vcsUrl),
                        revision = vcsRevision,
                        path = vcsDir.getPathToRoot(opensslPackageFile.parentFile)
                    ),
                    homepageUrl = "https://www.openssl.org/",
                    scopeDependencies = setOf(
                        Scope("default")
                    )
                ),
                emptySet()
            )

            actualResult[zlibId] shouldBe ProjectAnalyzerResult(
                Project(
                    id = zlibId,
                    cpe = "cpe:/a:compress:zlib:1.2.11:::en-us",
                    definitionFilePath = vcsDir.getPathToRoot(zlibPackageFile),
                    authors = setOf("Jean-loup Gailly", "Mark Adler"),
                    declaredLicenses = setOf("Zlib"),
                    vcs = VcsInfo(
                        type = VcsType.GIT,
                        url = normalizeVcsUrl(vcsUrl),
                        revision = vcsRevision,
                        path = vcsDir.getPathToRoot(zlibPackageFile.parentFile)
                    ),
                    homepageUrl = "http://zlib.net",
                    scopeDependencies = setOf(
                        Scope("default")
                    )
                ),
                emptySet()
            )
        }

        "retrieve transitive dependencies" {
            val idCurl = Identifier("SpdxDocumentFile::curl:7.70.0")
            val idOpenSsl = Identifier("SpdxDocumentFile:OpenSSL Development Team:openssl:1.1.1g")
            val idZlib = Identifier("SpdxDocumentFile::zlib:1.2.11")
            val idMyLib = Identifier("SpdxDocumentFile::my-lib:8.88.8")

            val projectFile = projectDir.resolve("transitive-dependencies/project-xyz.spdx.yml")
            val definitionFiles = listOf(projectFile)

            val result = create("SpdxDocumentFile").resolveDependencies(definitionFiles, emptyMap())

            result.projectResults[projectFile] shouldNotBeNull {
                with(single()) {
                    val resolvedProject = project.withResolvedScopes(result.dependencyGraph)
                    resolvedProject.scopes.map { it.name } should containExactlyInAnyOrder("runtime", "default")

                    resolvedProject.scopes.first { it.name == "runtime" } shouldNotBeNull {
                        dependencies shouldHaveSize 1

                        val myLibRef = dependencies.first()
                        myLibRef.id shouldBe idMyLib
                        myLibRef.dependencies.map { it.id } should containExactlyInAnyOrder(idCurl, idOpenSsl)
                    }

                    packages.map { it.id } should containExactlyInAnyOrder(idZlib, idMyLib, idCurl, idOpenSsl)
                }
            }
        }
    }

    "mapDefinitionFiles()" should {
        "remove SPDX documents that do not describe a project if a project file is provided" {
            val projectFile = projectDir.resolve("package-references/project-xyz.spdx.yml")
            val packageFile = projectDir.resolve("libs/curl/package.spdx.yml")

            val definitionFiles = listOf(projectFile, packageFile)

            val result = create("SpdxDocumentFile").mapDefinitionFiles(definitionFiles)

            result should containExactly(projectFile)
        }

        "keep SPDX documents that do not describe a project if no project file is provided" {
            val packageFileCurl = projectDir.resolve("libs/curl/package.spdx.yml")
            val packageFileZlib = projectDir.resolve("libs/zlib/package.spdx.yml")

            val definitionFiles = listOf(packageFileCurl, packageFileZlib)

            val result = create("SpdxDocumentFile").mapDefinitionFiles(definitionFiles)

            result should containExactly(definitionFiles)
        }

        // TODO: Test that we can read in files written by SpdxDocumentReporter.
    }

    "createPackageManagerResult" should {
        "not include subproject dependencies as packages" {
            val projectFile = projectDir.resolve("subproject-dependencies/project-xyz.spdx.yml")
            val subProjectFile = projectDir.resolve("subproject-dependencies/subproject/subproject-xyz.spdx.yml")
            val definitionFiles = listOf(projectFile, subProjectFile)

            val result = create("SpdxDocumentFile").resolveDependencies(definitionFiles, emptyMap())
            val projectResults = result.projectResults.values.flatten()
            val projectIds = projectResults.map { it.project.id }
            val packageIds = projectResults.flatMap { projResult -> projResult.packages.map { it.id } }

            projectIds shouldContainExactlyInAnyOrder listOf(
                Identifier("SpdxDocumentFile::xyz:0.1.0"),
                Identifier("SpdxDocumentFile::subproject-xyz:0.1.0")
            )
            packageIds shouldContainExactlyInAnyOrder listOf(
                Identifier("SpdxDocumentFile::curl:7.70.0"),
                Identifier("SpdxDocumentFile::my-lib:8.88.8"),
                Identifier("SpdxDocumentFile:OpenSSL Development Team:openssl:1.1.1g")
            )
        }
    }
})

private val projectDir = getAssetFile("projects/synthetic")
private val vcsDir = checkNotNull(VersionControlSystem.forDirectory(projectDir))
private val vcsUrl = vcsDir.getRemoteUrl()
private val vcsRevision = vcsDir.getRevision()
