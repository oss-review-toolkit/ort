/*
 * Copyright (C) 2021 Bosch.IO GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

import org.ossreviewtoolkit.model.Hash
import org.ossreviewtoolkit.model.RemoteArtifact
import org.ossreviewtoolkit.model.VcsInfo
import org.ossreviewtoolkit.model.VcsType
import org.ossreviewtoolkit.spdx.SpdxConstants
import org.ossreviewtoolkit.spdx.model.SpdxPackage

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

class SpdxDocumentFileTest : WordSpec({
    "getBinaryArtifact()" should {
        "return a RemoteArtifact for a downloadLocation that points to a binary artifact" {
            getBinaryArtifact(pkgForBinaryArtifact) shouldBe RemoteArtifact(
                url = "https://repo1.maven.org/maven2/junit/junit/4.11/junit-4.11.jar",
                hash = Hash.NONE
            )
        }

        "return null for a downloadLocation that does not point to a binary artifact" {
            getBinaryArtifact(pkgForBinaryArtifact.copy(downloadLocation = SpdxConstants.NONE)) should beNull()
            getBinaryArtifact(pkgForBinaryArtifact.copy(downloadLocation = SpdxConstants.NOASSERTION)) should beNull()
            getBinaryArtifact(pkgForSourceArtifact) should beNull()
            getBinaryArtifact(pkgForVcs) should beNull()
        }
    }

    "getSourceArtifact()" should {
        "return a RemoteArtifact for a downloadLocation that points to a source artifact" {
            getSourceArtifact(pkgForSourceArtifact) shouldBe RemoteArtifact(
                url = "http://ftp.gnu.org/gnu/glibc/glibc-ports-2.15.tar.gz",
                hash = Hash.NONE
            )
        }

        "return null for a downloadLocation that does not point to a source artifact" {
            getSourceArtifact(pkgForSourceArtifact.copy(downloadLocation = SpdxConstants.NONE)) should beNull()
            getSourceArtifact(pkgForSourceArtifact.copy(downloadLocation = SpdxConstants.NOASSERTION)) should beNull()
            getSourceArtifact(pkgForBinaryArtifact) should beNull()
            getSourceArtifact(pkgForVcs) should beNull()
        }
    }

    "getVcsInfo()" should {
        "return the VcsInfo for a downloadLocation that points to a VCS" {
            getVcsInfo(pkgForVcs) shouldBe VcsInfo(
                type = VcsType.GIT,
                url = "https://git.myproject.org/MyProject.git",
                revision = "master",
                path = "/src/MyClass.cpp"
            )
        }

        "return null for a downloadLocation that does not point to a VCS" {
            getVcsInfo(pkgForVcs.copy(downloadLocation = SpdxConstants.NONE)) should beNull()
            getVcsInfo(pkgForVcs.copy(downloadLocation = SpdxConstants.NOASSERTION)) should beNull()
            getVcsInfo(pkgForBinaryArtifact) should beNull()
            getVcsInfo(pkgForSourceArtifact) should beNull()
        }
    }
})
