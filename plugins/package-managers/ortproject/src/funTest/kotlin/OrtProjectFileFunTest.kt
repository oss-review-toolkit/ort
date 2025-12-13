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
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith

import org.ossreviewtoolkit.analyzer.analyze
import org.ossreviewtoolkit.analyzer.getAnalyzerResult
import org.ossreviewtoolkit.analyzer.resolveSingleProject
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.test.getAssetFile

class OrtProjectFileFunTest : WordSpec({
    "resolveDependencies()" should {
        "return properly resolved packages list based on json file" {
            val definitionFile = getAssetFile("projects/ort.project.json")
            verifyBasicProject(OrtProjectFileFactory.create().resolveSingleProject(definitionFile))
        }

        "return properly resolved packages list based on yaml file" {
            val definitionFile = getAssetFile("projects/ort.project.yml")
            verifyBasicProject(OrtProjectFileFactory.create().resolveSingleProject(definitionFile))
        }

        "return issue when json file is wrongly formatted" {
            val definitionFile = getAssetFile("projects/malformed.ort.project.json")
            val project = OrtProjectFileFactory.create().resolveSingleProject(definitionFile)
            project.packages.size shouldBe 0
            project.issues.size shouldBe 1

            with(project.issues[0]) {
                message shouldStartWith "Unexpected JSON token at offset"
                source shouldBe "OrtProjectFile"
            }
        }

        "return issue when yaml file is wrongly formatted" {
            val definitionFile = getAssetFile("projects/malformed.ort.project.yml")
            val project = OrtProjectFileFactory.create().resolveSingleProject(definitionFile)
            project.packages.size shouldBe 0
            project.issues.size shouldBe 1

            with(project.issues[0]) {
                message shouldStartWith "while parsing a block mapping"
                source shouldBe "OrtProjectFile"
            }
        }

        "return issue when there is no package id or purl defined" {
            val definitionFile = getAssetFile("projects/no-pkg-id-or-purl.ort.project.yml")
            val project = OrtProjectFileFactory.create().resolveSingleProject(definitionFile)
            project.packages.size shouldBe 0
            project.issues.size shouldBe 1

            with(project.issues[0]) {
                message shouldContain("There is no id or purl defined for the package.")
                source shouldBe "OrtProjectFile"
            }
        }

        "use all of files in structure for package mapping" {
            val definitionFile = getAssetFile("multiproject/ort.project.yml")
            val result = analyze(projectDir = definitionFile.parentFile).getAnalyzerResult()

            with(result) {
                projects.size shouldBe 3
                val projectNames = projects.map { it.id.name }
                projectNames.shouldContainAll(setOf("project-x", "subproject-x-1", "subproject-x-2"))

                packages.size shouldBe 4
                val packageNames = packages.map { it.purl }
                packageNames.shouldContainAll(
                    setOf(
                        "pkg:maven/com.example/sub-x1@9.98.0",
                        "pkg:maven/com.example/sub-x2@1.1.0",
                        "pkg:maven/com.example/full@1.1.0",
                        "pkg:maven/com.example/minimal@0.1.0"
                    )
                )
            }
        }
    }
})

private fun verifyBasicProject(result: ProjectAnalyzerResult) {
    with(result) {
        with(project.id) {
            name shouldBe "Example ORT project"
            type shouldBe "OrtProjectFile"
        }

        project.authors shouldContainAll setOf("John Doe", "Foo Bar")

        project.vcs shouldNotBe null

        // VCS values may be different depending on the test environment,
        // so just check for basic validity.
        with(project.vcs) {
            type shouldNotBe VcsType.UNKNOWN
            url.length shouldBeGreaterThan 0
            revision.length shouldBeGreaterThan 0
            path shouldBe "plugins/package-managers/ortproject/src/funTest/assets/projects"
        }

        project.homepageUrl shouldBe "https://project_x.example.com"

        project.scopeDependencies?.size shouldBe 2
        project.scopeDependencies?.map { it.name }?.shouldContainAll(setOf("main", "some_scope"))

        project.scopeDependencies?.find { it.name == "main" }.should {
            it?.dependencies?.size shouldBe 1
            it?.dependencies?.map { dep -> dep.id.name }?.shouldContainAll(setOf("full"))
        }

        project.scopeDependencies?.find { it.name == "some_scope" }.should {
            it?.dependencies?.size shouldBe 1
            it?.dependencies?.map { dep -> dep.id.name }?.shouldContainAll(setOf("full"))
        }

        packages.size shouldBe 2
        packages.map { it.purl } shouldContainAll setOf(
            "pkg:maven/com.example/full@1.1.0",
            "pkg:maven/com.example/minimal@0.1.0"
        )

        packages.find { it.purl == "pkg:maven/com.example/full@1.1.0" }.should {
            it?.description shouldBe "Package with fully elaborated model."

            it?.declaredLicenses shouldNotBe null
            it?.declaredLicenses?.size.shouldBe(2)
            it?.declaredLicenses?.shouldContainExactly(setOf("Apache-2.0", "MIT"))

            it?.vcs shouldNotBe null
            it?.vcs?.type shouldBe VcsType.MERCURIAL
            it?.vcs?.url shouldBe "https://git.example.com/full/"
            it?.vcs?.revision shouldBe "master"
            it?.vcs?.path shouldBe "/"

            it?.homepageUrl shouldBe "https://project_x.example.com/full"

            it?.sourceArtifact shouldNotBe null
            it?.sourceArtifact?.url shouldBe "https://repo.example.com/m2/full-1.1.0-sources.jar"

            it?.labels?.size.shouldBe(2)
            it?.authors?.shouldContainAll(setOf("Doe John", "Bar Foo"))
        }

        packages.find { it.purl == "pkg:maven/com.example/minimal@0.1.0" }.should {
            it?.description shouldBe ""

            it?.declaredLicenses?.size.shouldBe(0)

            it?.vcs shouldBe VcsInfo.EMPTY
            it?.sourceArtifact shouldBe RemoteArtifact.EMPTY

            it?.labels shouldBe emptyMap()
            it?.authors shouldBe emptySet()
        }
    }
}
