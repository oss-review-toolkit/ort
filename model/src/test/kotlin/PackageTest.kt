/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package com.here.ort.model

import io.kotlintest.shouldBe
import io.kotlintest.shouldThrow
import io.kotlintest.specs.StringSpec

class PackageTest : StringSpec() {
    init {
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
                declaredLicenses = sortedSetOf("declared license"),
                description = "description",
                homepageUrl = "homepageUrl",
                binaryArtifact = RemoteArtifact("url", Hash.create("hash")),
                sourceArtifact = RemoteArtifact("url", Hash.create("hash")),
                vcs = VcsInfo(VcsType.UNKNOWN, "url", "revision")
            )

            val other = Package(
                id = Identifier(
                    type = "type",
                    namespace = "namespace",
                    name = "name",
                    version = "version"
                ),
                declaredLicenses = sortedSetOf("other declared license"),
                description = "other description",
                homepageUrl = "other homepageUrl",
                binaryArtifact = RemoteArtifact("other url", Hash.create("other hash")),
                sourceArtifact = RemoteArtifact("other url", Hash.create("other hash")),
                vcs = VcsInfo(VcsType.UNKNOWN, "other url", "other revision")
            )

            val diff = pkg.diff(other)

            diff.binaryArtifact shouldBe pkg.binaryArtifact
            diff.comment shouldBe null
            diff.declaredLicenses shouldBe pkg.declaredLicenses
            diff.homepageUrl shouldBe pkg.homepageUrl
            diff.sourceArtifact shouldBe pkg.sourceArtifact
            diff.vcs shouldBe pkg.vcs.toCuration()
        }

        "diff result does not contain unchanged values" {
            val pkg = Package(
                id = Identifier(
                    type = "type",
                    namespace = "namespace",
                    name = "name",
                    version = "version"
                ),
                declaredLicenses = sortedSetOf("declared license"),
                description = "description",
                homepageUrl = "homepageUrl",
                binaryArtifact = RemoteArtifact("url", Hash.create("hash")),
                sourceArtifact = RemoteArtifact("url", Hash.create("hash")),
                vcs = VcsInfo(VcsType.UNKNOWN, "url", "revision")
            )

            val diff = pkg.diff(pkg)

            diff.binaryArtifact shouldBe null
            diff.comment shouldBe null
            diff.declaredLicenses shouldBe null
            diff.homepageUrl shouldBe null
            diff.sourceArtifact shouldBe null
            diff.vcs shouldBe null
        }
    }
}
