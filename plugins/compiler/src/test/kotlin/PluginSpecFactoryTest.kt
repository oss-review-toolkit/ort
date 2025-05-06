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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.plugins.compiler.derivePluginId

class PluginSpecFactoryTest : WordSpec({
    "derivePluginId" should {
        "return the plugin ID for a parent class that is not a suffix" {
            derivePluginId("Bazel", "PackageManager") shouldBe "bazel"
        }

        "return the plugin ID for a parent class that is a suffix" {
            derivePluginId("WebAppReporter", "Reporter") shouldBe "web-app"
        }

        "return the plugin ID for a parent class with an 'Ort' prefix" {
            derivePluginId("AdviseCommand", "OrtCommand") shouldBe "advise"
        }

        "return the plugin ID for a camel-case plugin class name" {
            derivePluginId("UploadResultToPostgresCommand", "OrtCommand") shouldBe "upload-result-to-postgres"
        }
    }
})
