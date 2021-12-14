/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.SpdxModelMapper
import org.ossreviewtoolkit.utils.spdx.model.SpdxDocument
import org.ossreviewtoolkit.utils.spdx.model.SpdxPackage

/*
 * The below package data is based on example data taken from the SPDX specification.
 */

private val pkgForBinaryArtifact = SpdxPackage(
    spdxId = "SPDXRef-Package-Dummy",
    downloadLocation = "https://repo1.maven.org/maven2/junit/junit/4.11/junit-4.11.jar",
    filesAnalyzed = false,
    copyrightText = "Copyright 2008-2010 John Smith",
    licenseConcluded = "NOASSERTION",
    licenseDeclared = "NOASSERTION",
    name = "Dummy"
)

private val pkgForSourceArtifact = SpdxPackage(
    spdxId = "SPDXRef-Package-Dummy",
    downloadLocation = "http://ftp.gnu.org/gnu/glibc/glibc-ports-2.15.tar.gz",
    filesAnalyzed = true,
    copyrightText = "Copyright 2008-2010 John Smith",
    licenseConcluded = "NOASSERTION",
    licenseDeclared = "NOASSERTION",
    name = "Dummy"
)

private val pkgForVcs = SpdxPackage(
    spdxId = "SPDXRef-Package-Dummy",
    downloadLocation = "git+https://git.myproject.org/MyProject.git@master#/src/MyClass.cpp",
    copyrightText = "Copyright 2008-2010 John Smith",
    licenseConcluded = "NOASSERTION",
    licenseDeclared = "NOASSERTION",
    name = "Dummy"
)

private fun createSpdxDocument(filename: String): SpdxDocument {
    val projectDir = File("src/funTest/assets/projects/synthetic/spdx").absoluteFile
    val definitionFile = projectDir.resolve(filename)
    return SpdxModelMapper.read(definitionFile)
}

class SpdxDocumentFileTest : WordSpec({
    "getVcsInfo()" should {
        "return the VcsInfo for a downloadLocation that points to a VCS" {
            pkgForVcs.getVcsInfo() shouldBe VcsInfo(
                type = VcsType.GIT,
                url = "https://git.myproject.org/MyProject.git",
                revision = "master",
                path = "/src/MyClass.cpp"
            )
        }

        "return null for a downloadLocation that does not point to a VCS" {
            pkgForVcs.copy(downloadLocation = SpdxConstants.NONE).getVcsInfo() should beNull()
            pkgForVcs.copy(downloadLocation = SpdxConstants.NOASSERTION).getVcsInfo() should beNull()
            pkgForBinaryArtifact.getVcsInfo() should beNull()
            pkgForSourceArtifact.getVcsInfo() should beNull()
        }
    }

    "projectPackage()" should {
        "return project package when list of packages is given" {
            val spdxDocument = createSpdxDocument("project-xyz-with-inline-packages.spdx.yml")

            spdxDocument.projectPackage()?.spdxId shouldBe "SPDXRef-Package-xyz"
        }

        "return project package when only one package in list, but external document references exist" {
            val spdxDocument = createSpdxDocument("project-xyz-with-package-references.spdx.yml")

            spdxDocument.projectPackage()?.spdxId shouldBe "SPDXRef-Package-xyz"
        }

        "return no project package when just one package in list" {
            val spdxDocument = createSpdxDocument("libs/curl/package.spdx.yml")

            spdxDocument.projectPackage() should beNull()
        }
    }
})
