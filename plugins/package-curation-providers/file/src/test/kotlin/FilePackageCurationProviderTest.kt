/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagecurationproviders.file

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package

class FilePackageCurationProviderTest : StringSpec() {
    private val curationsDir = File("src/test/assets/package-curations-dir")
    private val curationsFile = File("src/test/assets/package-curations.yml")

    init {
        "Provider can read YAML file" {
            val provider = FilePackageCurationProvider(curationsFile)

            provider.packageCurations should haveSize(8)
        }

        "Provider can read from multiple yaml files" {
            val provider = FilePackageCurationProvider(curationsDir)
            val expectedCurationIds = listOf(
                "Maven:org.foo::",
                "Maven:org.ossreviewtoolkit::"
            ).map { Identifier(it) }

            provider.packageCurations.map { it.id } shouldContainExactlyInAnyOrder expectedCurationIds
        }

        "Provider can read from a single file and directories" {
            val provider = FilePackageCurationProvider(curationsFile, curationsDir)
            val expectedCurationIds = listOf(
                // File
                "NPM::ramda:[0.21.0,0.25.0]",
                // Directory
                "Maven:org.foo::",
                "Maven:org.ossreviewtoolkit::"
            ).map { Identifier(it) }

            provider.packageCurations.map { it.id } shouldContainAll expectedCurationIds
        }

        "Provider returns only matching curations for a fixed version" {
            val provider = FilePackageCurationProvider(curationsFile)

            val identifier = Identifier("maven", "org.hamcrest", "hamcrest-core", "1.3")
            val packages = listOf(Package.EMPTY.copy(id = identifier))
            val curations = provider.getCurationsFor(packages)

            curations should haveSize(4)
            curations.forEach {
                it.isApplicable(identifier) shouldBe true
            }
            (provider.packageCurations - curations).forEach {
                it.isApplicable(identifier) shouldBe false
            }
        }

        "Provider returns only matching curations for a version range" {
            val provider = FilePackageCurationProvider(curationsFile)

            val idMinVersion = Identifier("npm", "", "ramda", "0.21.0")
            val idMaxVersion = Identifier("npm", "", "ramda", "0.25.0")
            val idOutVersion = Identifier("npm", "", "ramda", "0.26.0")

            val curationsMinVersion = provider.getCurationsFor(createPackagesFromIds(idMinVersion))
            val curationsMaxVersion = provider.getCurationsFor(createPackagesFromIds(idMaxVersion))
            val curationsOutVersion = provider.getCurationsFor(createPackagesFromIds(idOutVersion))

            curationsMinVersion should haveSize(1)
            (provider.packageCurations - curationsMinVersion).forEach {
                it.isApplicable(idMinVersion) shouldBe false
            }

            curationsMaxVersion should haveSize(1)
            (provider.packageCurations - curationsMaxVersion).forEach {
                it.isApplicable(idMinVersion) shouldBe false
            }

            curationsOutVersion should beEmpty()
        }

        "Provider throws an exception if the curations file is not de-serializable" {
            val curationsFile = File("src/test/assets/package-curations-not-deserializable.yml")

            shouldThrow<Exception> {
                FilePackageCurationProvider(curationsFile)
            }
        }

        "Provider throws an exception if the curations file does not exist" {
            val curationsFile = File("src/test/assets/package-curations-not-existing.yml")

            shouldThrow<Exception> {
                FilePackageCurationProvider(curationsFile)
            }
        }
    }
}

private fun createPackagesFromIds(vararg ids: Identifier) =
    ids.map { Package.EMPTY.copy(id = it) }
