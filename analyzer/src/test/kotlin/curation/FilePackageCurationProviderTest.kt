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

package org.ossreviewtoolkit.analyzer.curation

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.model.FileFormat
import org.ossreviewtoolkit.model.Identifier

class FilePackageCurationProviderTest : StringSpec() {
    private val curationsDir = File("src/test/assets/package-curations-dir")
    private val curationsFile = File("src/test/assets/package-curations.yml")

    init {
        "Provider can read YAML file" {
            val provider = FilePackageCurationProvider(curationsFile)

            provider.packageCurations should haveSize(8)
        }

        "Provider can read from multiple yaml files" {
            val provider = FilePackageCurationProvider(FileFormat.findFilesWithKnownExtensions(curationsDir))
            val idsWithExistingCurations = listOf(
                Identifier("maven", "org.ossreviewtoolkit", "example", "1.0"),
                Identifier("maven", "org.foo", "bar", "0.42")
            )

            idsWithExistingCurations.forEach {
                val curations = provider.getCurationsFor(listOf(it)).values.flatten()

                curations should haveSize(1)
            }
        }

        "Provider can read from a single file and directories" {
            val provider = FilePackageCurationProvider.from(curationsFile, curationsDir)

            val idsWithExistingCurations = listOf(
                // File
                Identifier("npm", "", "ramda", "0.21.0"),
                // Directory
                Identifier("maven", "org.ossreviewtoolkit", "example", "1.0"),
                Identifier("maven", "org.foo", "bar", "0.42")
            )

            idsWithExistingCurations.forEach {
                val curations = provider.getCurationsFor(listOf(it)).values.flatten()

                curations should haveSize(1)
            }
        }

        "Provider returns only matching curations for a fixed version" {
            val provider = FilePackageCurationProvider(curationsFile)

            val identifier = Identifier("maven", "org.hamcrest", "hamcrest-core", "1.3")
            val curations = provider.getCurationsFor(listOf(identifier)).values.flatten()

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

            val curationsMinVersion = provider.getCurationsFor(listOf(idMinVersion)).values.flatten()
            val curationsMaxVersion = provider.getCurationsFor(listOf(idMaxVersion)).values.flatten()
            val curationsOutVersion = provider.getCurationsFor(listOf(idOutVersion)).values.flatten()

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
