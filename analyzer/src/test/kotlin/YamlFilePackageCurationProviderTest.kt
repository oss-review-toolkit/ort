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

package com.here.ort.analyzer

import com.here.ort.model.Identifier

import io.kotlintest.matchers.beEmpty
import io.kotlintest.matchers.haveSize
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.File

class YamlFilePackageCurationProviderTest : StringSpec() {
    private val curationsFile = File("src/test/assets/package-curations.yml")

    init {
        "Provider can read YAML file" {
            val provider = YamlFilePackageCurationProvider(curationsFile)

            provider.packageCurations should haveSize(8)
        }

        "Provider returns only matching curations for a fixed version" {
            val provider = YamlFilePackageCurationProvider(curationsFile)

            val identifier = Identifier("maven", "org.hamcrest", "hamcrest-core", "1.3")
            val curations = provider.getCurationsFor(identifier)

            curations should haveSize(4)
            curations.forEach {
                it.id.matches(identifier) shouldBe true
            }
            (provider.packageCurations - curations).forEach {
                it.id.matches(identifier) shouldBe false
            }
        }

        "Provider returns only matching curations for a version range" {
            val provider = YamlFilePackageCurationProvider(curationsFile)

            val idMinVersion = Identifier("npm", "", "ramda", "0.21.0")
            val idMaxVersion = Identifier("npm", "", "ramda", "0.25.0")
            val idOutVersion = Identifier("npm", "", "ramda", "0.26.0")

            val curationsMinVersion = provider.getCurationsFor(idMinVersion)
            val curationsMaxVersion = provider.getCurationsFor(idMaxVersion)
            val curationsOutVersion = provider.getCurationsFor(idOutVersion)

            curationsMinVersion should haveSize(1)
            (provider.packageCurations - curationsMinVersion).forEach {
                it.id.matches(idMinVersion) shouldBe false
            }

            curationsMaxVersion should haveSize(1)
            (provider.packageCurations - curationsMaxVersion).forEach {
                it.id.matches(idMinVersion) shouldBe false
            }

            curationsOutVersion should beEmpty()
        }
    }
}
