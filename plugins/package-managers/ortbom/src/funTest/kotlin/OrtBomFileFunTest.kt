/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.ortbom

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

import org.ossreviewtoolkit.analyzer.resolveSingleProject
import org.ossreviewtoolkit.model.ProjectAnalyzerResult
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.test.getAssetFile

class OrtBomFileFunTest : WordSpec({
    "resolveDependencies()" should {
        "return properly resolved packages list based on json file" {
            val definitionFile = getAssetFile("projects/ort-bom.json")
            verifyBasicProject(OrtBomFileFactory.create().resolveSingleProject(definitionFile))
        }

        "return properly resolved packages list based on yaml file" {
            val definitionFile = getAssetFile("projects/ort-bom.yml")
            verifyBasicProject(OrtBomFileFactory.create().resolveSingleProject(definitionFile))
        }

        "return issues when package name is wrong" {
            val definitionFile = getAssetFile("projects/ort-bom-wrong-pkg-name.json")
            val project = OrtBomFileFactory.create().resolveSingleProject(definitionFile)
            project.packages.size shouldBe 0
            project.issues.size shouldBe 1

            with(project.issues[0]) {
                message shouldBe "OrtBomFile failed to resolve dependencies for path 'ort-bom-wrong-pkg-name.json': " +
                    "IllegalArgumentException: Parsing error: invalid schema: something, in PURL " +
                    "something:maven/com.example/full@1.1.0"
                source shouldBe "OrtBomFile"
            }
        }
    }
})

private fun verifyBasicProject(result: ProjectAnalyzerResult) {
    with(result) {
        with(project.id) {
            name shouldBe "Example ORT BOM project"
            type shouldBe "OrtBom"
        }

        project.authors shouldContainAll setOf("John Doe", "Foo Bar")

        project.vcs shouldNotBe null

        with(project.vcs) {
            type shouldBe VcsType.GIT
            url shouldBe "https://git.example.com/project_x/"
            revision shouldBe "master"
            path shouldBe "/"
        }

        project.homepageUrl shouldBe "https://project_x.example.com"

        project.scopeDependencies shouldNotBe null
        project.scopeDependencies?.map { it.name }?.shouldContainAll(setOf("main", "excluded"))

        project.scopeDependencies?.find { it.name == "main" }.should {
            it?.dependencies?.size shouldBe 2
            it?.dependencies?.map { dep -> dep.id.name }?.shouldContainAll(setOf("minimal", "full"))
        }

        project.scopeDependencies?.find { it.name == "excluded" }.should {
            it?.dependencies?.size shouldBe 1
            it?.dependencies?.map { dep -> dep.id.name }?.shouldContainAll(setOf("test"))
        }

        packages.size shouldBe 3
        packages.map { it.purl } shouldContainAll setOf(
            "pkg:maven/com.example/full@1.1.0",
            "pkg:maven/com.example/minimal@0.1.0",
            "pkg:maven/com.example/test@1.0"
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
        }

        packages.find { it.purl == "pkg:maven/com.example/minimal@0.1.0" }.should {
            it?.description shouldBe "Package with minimal fields required."

            it?.declaredLicenses?.size.shouldBe(1)
            it?.declaredLicenses?.shouldContainExactly(setOf("MIT"))

            it?.vcs shouldBe VcsInfo.EMPTY
            it?.sourceArtifact shouldBe RemoteArtifact.EMPTY

            it?.labels shouldBe emptyMap()
        }

        packages.find { it.purl == "pkg:maven/com.example/test@1.0" }.should {
            it?.description shouldBe "Package for test purposes, should be excluded."

            it?.declaredLicenses?.size.shouldBe(1)
            it?.declaredLicenses?.shouldContainExactly(setOf("GNU v2.0"))

            it?.vcs shouldBe VcsInfo.EMPTY
            it?.sourceArtifact shouldBe RemoteArtifact.EMPTY

            it?.labels shouldBe emptyMap()
        }
    }
}
