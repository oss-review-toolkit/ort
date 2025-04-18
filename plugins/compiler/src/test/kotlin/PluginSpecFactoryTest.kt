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

package org.ossreviewtoolkit.plugins.compiler

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

class PluginSpecFactoryTest : WordSpec({
    "derivePluginId()" should {
        "return the plugin ID for advice-providers" {
            derivePluginId("BlackDuck", "AdviceProvider") shouldBe "BlackDuck"
        }

        "return the plugin ID for commands" {
            derivePluginId("AdviseCommand", "OrtCommand") shouldBe "Advise"
        }

        "return the plugin ID for package-configuration-providers" {
            derivePluginId("DirPackageConfigurationProvider", "PackageConfigurationProvider") shouldBe "Dir"
        }

        "return the plugin ID for package-curation-providers" {
            derivePluginId("ClearlyDefinedPackageCurationProvider", "PackageCurationProvider") shouldBe "ClearlyDefined"
        }

        "return the plugin ID for package-managers" {
            derivePluginId("Bazel", "PackageManager") shouldBe "Bazel"
        }

        "return the plugin ID for reporters" {
            derivePluginId("CtrlXAutomationReporter", "Reporter") shouldBe "CtrlXAutomation"
        }

        "return the plugin ID for scanners" {
            derivePluginId("Askalono", "LocalPathScannerWrapper") shouldBe "Askalono"
        }

        "return the plugin ID for version-control-systems" {
            derivePluginId("Git", "VersionControlSystem") shouldBe "Git"
        }
    }
})
