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

package org.ossreviewtoolkit.plugins.advisors.blackduck

import com.blackduck.integration.blackduck.api.generated.view.VulnerabilityView

import com.google.gson.GsonBuilder

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.model.toYaml
import org.ossreviewtoolkit.utils.test.matchExpectedResult

class BlackDuckTest : WordSpec({
    "toOrtVulnerability()" should {
        "parse a vulnerability with CVSS 3.1 and with duplicate links as expected" {
            val expectedResult = getAssetFile("BDSA-2024-5272-parsed.yml")
            val vulnerabilityView = readVulnerabilityViewAssetFile("BDSA-2024-5272.json")

            val vulnerability = vulnerabilityView.toOrtVulnerability()

            vulnerability.toYaml() shouldBe matchExpectedResult(expectedResult)
        }
    }
})

private fun readVulnerabilityViewAssetFile(path: String): VulnerabilityView =
    GSON.fromJson(getAssetFile(path).readText(), VulnerabilityView::class.java)

private val GSON by lazy { GsonBuilder().setPrettyPrinting().create() }

private fun getAssetFile(path: String): File = File("src/test/assets", path).absoluteFile
