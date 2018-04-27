/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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
import com.here.ort.utils.searchUpwardsForSubdirectory

import io.kotlintest.matchers.haveSize
import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec

import java.io.File

class YamlFilePackageCurationProviderTest : StringSpec() {
    private val rootDir = File(".").searchUpwardsForSubdirectory(".git")!!
    private val curationsFile = File(rootDir, "analyzer/src/test/assets/package-curations.yml")

    init {
        "Provider can read YAML file" {
            val provider = YamlFilePackageCurationProvider(curationsFile)

            provider.packageCurations should haveSize(7)
        }

        "Provider returns only matching curations" {
            val identifier = Identifier("maven", "org.hamcrest", "hamcrest-core", "1.3")
            val provider = YamlFilePackageCurationProvider(curationsFile)

            val curations = provider.getCurationsFor(identifier)

            curations should haveSize(4)
            curations.forEach {
                it.id.matches(identifier) shouldBe true
            }
            (provider.packageCurations - curations).forEach {
                it.id.matches(identifier) shouldBe false
            }
        }
    }
}
