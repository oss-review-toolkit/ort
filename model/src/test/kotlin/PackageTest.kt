/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.model

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

class PackageTest : StringSpec({
    "diff throws an exception if the identifiers are not equals" {
        val pkg = Package.EMPTY.copy(id = Identifier("type1", "namespace1", "name1", "version1"))
        val other = Package.EMPTY.copy(id = Identifier("type2", "namespace2", "name2", "version2"))

        shouldThrow<IllegalArgumentException> {
            pkg.diff(other)
        }
    }

    "diff result contains all changed values" {
        val pkg = Package(
            id = Identifier(
                type = "type",
                namespace = "namespace",
                name = "name",
                version = "version"
            ),
            authors = sortedSetOf("author"),
            declaredLicenses = sortedSetOf("declared license"),
            description = "description",
            homepageUrl = "homepageUrl",
            binaryArtifact = RemoteArtifact("url", Hash.create("hash")),
            sourceArtifact = RemoteArtifact("url", Hash.create("hash")),
            vcs = VcsInfo(VcsType("type"), "url", "revision"),
            isMetaDataOnly = false
        )

        val other = Package(
            id = Identifier(
                type = "type",
                namespace = "namespace",
                name = "name",
                version = "version"
            ),
            authors = sortedSetOf("other author"),
            declaredLicenses = sortedSetOf("other declared license"),
            description = "other description",
            homepageUrl = "other homepageUrl",
            binaryArtifact = RemoteArtifact("other url", Hash.create("other hash")),
            sourceArtifact = RemoteArtifact("other url", Hash.create("other hash")),
            vcs = VcsInfo(VcsType("other type"), "other url", "other revision"),
            isMetaDataOnly = true
        )

        val diff = pkg.diff(other)

        diff.binaryArtifact shouldBe pkg.binaryArtifact
        diff.comment should beNull()
        diff.authors shouldBe pkg.authors
        diff.homepageUrl shouldBe pkg.homepageUrl
        diff.sourceArtifact shouldBe pkg.sourceArtifact
        diff.vcs shouldBe pkg.vcsProcessed.toCuration()
        diff.isMetaDataOnly shouldBe pkg.isMetaDataOnly
    }

    "diff result does not contain unchanged values" {
        val pkg = Package(
            id = Identifier(
                type = "type",
                namespace = "namespace",
                name = "name",
                version = "version"
            ),
            authors = sortedSetOf("author"),
            declaredLicenses = sortedSetOf("declared license"),
            description = "description",
            homepageUrl = "homepageUrl",
            binaryArtifact = RemoteArtifact("url", Hash.create("hash")),
            sourceArtifact = RemoteArtifact("url", Hash.create("hash")),
            vcs = VcsInfo(VcsType("type"), "url", "revision")
        )

        val diff = pkg.diff(pkg)

        diff.binaryArtifact should beNull()
        diff.comment should beNull()
        diff.authors should beNull()
        diff.homepageUrl should beNull()
        diff.sourceArtifact should beNull()
        diff.vcs should beNull()
        diff.isMetaDataOnly should beNull()
    }
})
