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

import io.mockk.every
import io.mockk.mockk

import java.io.File

import org.ossreviewtoolkit.analyzer.managers.utils.SpdxResolvedDocument
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.SpdxModelMapper
import org.ossreviewtoolkit.utils.spdx.model.SpdxDocument
import org.ossreviewtoolkit.utils.spdx.model.SpdxExternalReference
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
            val spdxDocument = createSpdxDocument("inline-packages/project-xyz.spdx.yml")

            spdxDocument.projectPackage()?.spdxId shouldBe "SPDXRef-Package-xyz"
        }

        "return project package when only one package in list, but external document references exist" {
            val spdxDocument = createSpdxDocument("package-references/project-xyz.spdx.yml")

            spdxDocument.projectPackage()?.spdxId shouldBe "SPDXRef-Package-xyz"
        }

        "return no project package when just one package in list" {
            val spdxDocument = createSpdxDocument("libs/curl/package.spdx.yml")

            spdxDocument.projectPackage() should beNull()
        }
    }

    "getPackageManagerDependency" should {
        "return null for an unknown package" {
            val doc = mockk<SpdxResolvedDocument>()
            doc.mockPackage(null)

            val manager = createPackageManager()

            manager.getPackageManagerDependency(pkgForVcs.spdxId, doc) should beNull()
        }

        "return null for a missing definition file" {
            val pkg = mockk<SpdxPackage>()
            val doc = mockk<SpdxResolvedDocument>()
            doc.mockPackage(pkg)
            doc.mockDefinitionFile(null)

            val manager = createPackageManager()

            manager.getPackageManagerDependency(pkgForVcs.spdxId, doc) shouldBe null
        }

        "return null for an undefined package file name" {
            val doc = mockk<SpdxResolvedDocument>()
            doc.mockPackage(pkgForVcs)
            doc.mockDefinitionFile(File("definition.spdx"))

            val manager = createPackageManager()

            manager.getPackageManagerDependency(pkgForVcs.spdxId, doc) shouldBe null
        }

        "return null if no external reference with a scope is defined" {
            val references = listOf(
                SpdxExternalReference(SpdxExternalReference.Type.Purl, "pkg:conan/test.org/t1@0.7"),
                SpdxExternalReference(SpdxExternalReference.Type.Purl, "pkg:conan/test.org/t2@0.7?foo=bar"),
                SpdxExternalReference(SpdxExternalReference.Type.Cpe22Type, "pkg:conan/test.org/t3@0.7?scope=scope")
            )
            val pkg = mockk<SpdxPackage>()
            every { pkg.externalRefs } returns references
            every { pkg.packageFilename } returns "somePackageFile.tst"

            val doc = mockk<SpdxResolvedDocument>()
            doc.mockPackage(pkg)
            doc.mockDefinitionFile(File("wrongReferences.spdx"))

            val manager = createPackageManager()

            manager.getPackageManagerDependency(pkgForVcs.spdxId, doc) shouldBe null
        }
    }

    "extractScopeFromExternalReferences" should {
        "extract the correct scope even if there are multiple URL parameters" {
            val reference = SpdxExternalReference(
                SpdxExternalReference.Type.Purl,
                "pkg:conan/test.org@1.2.3?foo=bar&x=y&scope=requires&one=more"
            )
            val pkg = pkgForVcs.copy(externalRefs = listOf(reference))

            pkg.extractScopeFromExternalReferences() shouldBe "requires"
        }
    }
})

/**
 * Create a [SpdxDocumentFile] instance to be used by tests.
 */
private fun createPackageManager(): SpdxDocumentFile =
    SpdxDocumentFile("test", File("."), AnalyzerConfiguration(), RepositoryConfiguration())

/**
 * Prepare this mock [SpdxResolvedDocument] to return [pkg] when queried for the test package.
 */
private fun SpdxResolvedDocument.mockPackage(pkg: SpdxPackage?) {
    every { getSpdxPackageForId(pkgForVcs.spdxId, any()) } returns pkg
}

/**
 * Prepare this mock [SpdxResolvedDocument] to return [file] when queried for the test definition file.
 */
private fun SpdxResolvedDocument.mockDefinitionFile(file: File?) {
    every { getDefinitionFile(pkgForVcs.spdxId) } returns file
}
