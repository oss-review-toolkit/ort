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

package org.ossreviewtoolkit.plugins.packagemanagers.ortproject

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty as beEmptyCollection
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.maps.beEmpty as beEmptyMap
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.beEmpty
import io.kotest.matchers.string.shouldContain

import kotlin.collections.find

import org.ossreviewtoolkit.analyzer.analyze
import org.ossreviewtoolkit.analyzer.getAnalyzerResult
import org.ossreviewtoolkit.analyzer.resolveSingleProject
import org.ossreviewtoolkit.model.HashAlgorithm
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.test.getAssetFile

class OrtProjectFileFunTest : WordSpec({
    "resolveDependencies()" should {
        "return properly resolved packages list based on json file" {
            val definitionFile = getAssetFile("projects/ortproject.json")
            verifyBasicProject(OrtProjectFileFactory.create().resolveSingleProject(definitionFile))
        }

        "return properly resolved packages list based on yaml file" {
            val definitionFile = getAssetFile("projects/ortproject.yml")
            verifyBasicProject(OrtProjectFileFactory.create().resolveSingleProject(definitionFile))
        }

        "return properly resolved package list for minimal project definition using purl" {
            val definitionFile = getAssetFile("projects/minimal-purl.ortproject.yml")
            val result = OrtProjectFileFactory.create().resolveSingleProject(definitionFile)

            with(result) {
                issues should beEmptyCollection()

                // VCS values may be different depending on the test environment,
                // so just check for basic validity.
                with(project.vcs) {
                    type shouldNotBe VcsType.UNKNOWN
                    url shouldNot beEmpty()
                    revision shouldNot beEmpty()
                    path shouldBe "plugins/package-managers/ort-project-file/src/funTest/assets/projects"
                }

                with(project.id) {
                    name shouldBe "unknown"
                    type shouldBe "OrtProjectFile"
                }

                project.authors should beEmptyCollection()
                project.scopeDependencies?.find { it.name == "unnamed" } shouldNotBeNull {
                    dependencies.map { dep -> dep.id.name } should containExactly("minimal")
                }

                packages.shouldBeSingleton {
                    it.purl shouldBe "pkg:maven/com.example/minimal@0.1.0"
                    it.id shouldBe Identifier("Maven:com.example:minimal:0.1.0")
                }
            }
        }

        "return properly resolved package list for minimal project definition using id" {
            val definitionFile = getAssetFile("projects/minimal-id.ortproject.yml")
            val result = OrtProjectFileFactory.create().resolveSingleProject(definitionFile)

            with(result) {
                issues should beEmptyCollection()

                // VCS values may be different depending on the test environment,
                // so just check for basic validity.
                with(project.vcs) {
                    type shouldNotBe VcsType.UNKNOWN
                    url shouldNot beEmpty()
                    revision shouldNot beEmpty()
                    path shouldBe "plugins/package-managers/ort-project-file/src/funTest/assets/projects"
                }

                with(project.id) {
                    name shouldBe "unknown"
                    type shouldBe "OrtProjectFile"
                }

                project.authors should beEmptyCollection()
                project.scopeDependencies?.find { it.name == "unnamed" } shouldNotBeNull {
                    dependencies.map { dep -> dep.id.name } should containExactly("minimal")
                }

                packages.shouldBeSingleton {
                    it.purl shouldBe "pkg:maven/com.example/minimal@0.1.0"
                    it.id shouldBe Identifier("Maven:com.example:minimal:0.1.0")
                }
            }
        }

        "return issue when hash has no algorithm defined" {
            val definitionFile = getAssetFile("projects/no-hash-alg.ortproject.yml")
            val project = OrtProjectFileFactory.create().resolveSingleProject(definitionFile)
            project.packages should beEmptyCollection()
            project.issues.shouldBeSingleton {
                it.message shouldContain "Property 'algorithm' is required but it is missing"
            }
        }

        "return issue when hash algorithm is unknown" {
            val definitionFile = getAssetFile("projects/wrong-hash-alg.ortproject.yml")
            val project = OrtProjectFileFactory.create().resolveSingleProject(definitionFile)
            project.issues should beEmptyCollection()
            project.packages.shouldBeSingleton {
                it.sourceArtifact.hash.value shouldBe "da39a3ee5e6b4b0d3255bfef95601890afd80709"
                it.sourceArtifact.hash.algorithm shouldBe HashAlgorithm.UNKNOWN
            }
        }

        "return lowercase hash value when input value is uppercase" {
            val definitionFile = getAssetFile("projects/uppercase-hash-val.ortproject.yml")
            val project = OrtProjectFileFactory.create().resolveSingleProject(definitionFile)
            project.issues should beEmptyCollection()
            project.packages.shouldBeSingleton {
                it.sourceArtifact.hash.value shouldBe "da39a3ee5e6b4b0d3255bfef95601890afd80709"
                it.sourceArtifact.hash.algorithm shouldBe HashAlgorithm.SHA1
            }
        }

        "return issue when there is no package id or purl defined" {
            val definitionFile = getAssetFile("projects/no-pkg-id-or-purl.ortproject.yml")
            val project = OrtProjectFileFactory.create().resolveSingleProject(definitionFile)
            project.packages should beEmptyCollection()
            project.issues.shouldBeSingleton {
                it.message shouldContain "There is no id or purl defined for the package."
                it.source shouldBe "ORT Project File"
            }
        }

        "return issue when package id is invalid" {
            val definitionFile = getAssetFile("projects/invalid-pkg-id.ortproject.yml")
            val project = OrtProjectFileFactory.create().resolveSingleProject(definitionFile)
            project.packages should beEmptyCollection()
            project.issues.shouldBeSingleton {
                it.message shouldContain "The id 'Maven:0.1.0::' is not a valid Identifier."
                it.source shouldBe "ORT Project File"
            }
        }

        "return issue when package purl is invalid" {
            val definitionFile = getAssetFile("projects/invalid-pkg-purl.ortproject.yml")
            val project = OrtProjectFileFactory.create().resolveSingleProject(definitionFile)
            project.packages should beEmptyCollection()
            project.issues.shouldBeSingleton {
                it.message shouldContain "The purl 'pkg/maven/com.example/minimal@0.1.0' is not a valid PackageURL."
                it.source shouldBe "ORT Project File"
            }
        }

        "return UNKNOWN when vcs info type and revision is empty" {
            val definitionFile = getAssetFile("projects/malformed-vcs.ortproject.yml")
            val project = OrtProjectFileFactory.create().resolveSingleProject(definitionFile)
            project.packages should beEmptyCollection()
            project.issues.shouldBeSingleton {
                it.message shouldContain "Fields [type, revision] are required"
            }
        }

        "use all of files in structure for package mapping" {
            val definitionFile = getAssetFile("multiproject/ortproject.yml")
            val result = analyze(projectDir = definitionFile.parentFile).getAnalyzerResult()

            with(result) {
                projects.map { it.id.name } should containExactlyInAnyOrder(
                    "project-x",
                    "subproject-x-1",
                    "subproject-x-2"
                )

                packages.map { it.purl } should containExactlyInAnyOrder(
                    "pkg:maven/com.example/sub-x1@9.98.0",
                    "pkg:maven/com.example/sub-x2@1.1.0",
                    "pkg:maven/com.example/full@1.1.0",
                    "pkg:maven/com.example/minimal@0.1.0"
                )
            }
        }
    }
})

private fun verifyBasicProject(result: ProjectAnalyzerResult) {
    with(result) {
        issues should beEmptyCollection()

        with(project.id) {
            name shouldBe "Example ORT project"
            type shouldBe "OrtProjectFile"
        }

        project.authors should containExactlyInAnyOrder("John Doe", "Foo Bar")

        project.vcs shouldNotBeNull {
            type shouldNotBe VcsType.UNKNOWN
            url shouldNot beEmpty()
            revision shouldNot beEmpty()
            path shouldBe "plugins/package-managers/ort-project-file/src/funTest/assets/projects"
        }

        project.homepageUrl shouldBe "https://project_x.example.com"

        project.scopeDependencies shouldNotBeNull {
            map { it.name } should containExactlyInAnyOrder("main", "some_scope", "unnamed")

            find { it.name == "main" } shouldNotBeNull {
                dependencies.map { dep -> dep.id.name } should containExactly("full")
            }

            find { it.name == "some_scope" } shouldNotBeNull {
                dependencies.map { dep -> dep.id.name } should containExactly("full")
            }

            find { it.name == "unnamed" } shouldNotBeNull {
                dependencies.map { dep -> dep.id.name } should containExactly("minimal")
            }
        }

        packages.map { it.purl } should containExactlyInAnyOrder(
            "pkg:maven/com.example/full@1.1.0",
            "pkg:maven/com.example/minimal@0.1.0"
        )

        packages.find { it.purl == "pkg:maven/com.example/full@1.1.0" }.shouldNotBeNull {
            description shouldBe "Package with fully elaborated model."

            declaredLicenses shouldNotBeNull {
                containExactlyInAnyOrder(setOf("Apache-2.0", "MIT"))
            }

            vcs shouldNotBeNull {
                type shouldBe VcsType.MERCURIAL
                url shouldBe "https://git.example.com/full/"
                revision shouldBe "master"
                path shouldBe "/"
            }

            homepageUrl shouldBe "https://project_x.example.com/full"

            sourceArtifact shouldNotBeNull {
                url shouldBe "https://repo.example.com/m2/full-1.1.0-sources.jar"
                hash.value shouldBe "da39a3ee5e6b4b0d3255bfef95601890afd80709"
                hash.algorithm.name shouldBe "SHA1"
            }

            authors should containExactlyInAnyOrder("Doe John", "Bar Foo")
            isModified shouldBe true
            isMetadataOnly shouldBe true
        }

        packages.find { it.purl == "pkg:maven/com.example/minimal@0.1.0" }.shouldNotBeNull {
            description should beEmpty()
            declaredLicenses should beEmptyCollection()

            vcs shouldBe VcsInfo.EMPTY
            sourceArtifact shouldBe RemoteArtifact.EMPTY

            labels should beEmptyMap()
            authors should beEmptyCollection()
            isModified shouldBe false
            isMetadataOnly shouldBe false
        }
    }
}
