/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.detekt

import io.gitlab.arturbosch.detekt.api.Config
import io.gitlab.arturbosch.detekt.test.lint

import io.kotest.core.TestConfiguration
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.haveSize
import io.kotest.matchers.should

import java.io.File

import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.common.safeMkdirs

class OrtPackageNamingTest : WordSpec({
    val rule = OrtPackageNaming(Config.empty)

    "OrtPackageNaming rule" should {
        "succeed for package in top-level module" {
            val file = createFile(
                dir = "model/src/main/kotlin",
                content = "package org.ossreviewtoolkit.model"
            )

            val findings = rule.lint(file.toPath())

            findings should beEmpty()
        }

        "succeed for sub-package in top-level module" {
            val file = createFile(
                dir = "model/src/main/kotlin/config",
                content = "package org.ossreviewtoolkit.model.config"
            )

            val findings = rule.lint(file.toPath())

            findings should beEmpty()
        }

        "succeed for package in second-level module" {
            val file = createFile(
                dir = "clients/osv/src/main/kotlin",
                content = "package org.ossreviewtoolkit.clients.osv"
            )

            val findings = rule.lint(file.toPath())

            findings should beEmpty()
        }

        "succeed for sub-package in second-level module" {
            val file = createFile(
                dir = "clients/osv/src/main/kotlin/utils",
                content = "package org.ossreviewtoolkit.clients.osv.utils"
            )

            val findings = rule.lint(file.toPath())

            findings should beEmpty()
        }

        "succeed for package in third-level module" {
            val file = createFile(
                dir = "plugins/package-curation-providers/file/src/main/kotlin",
                content = "package org.ossreviewtoolkit.plugins.packagecurationproviders.file"
            )

            val findings = rule.lint(file.toPath())

            findings should beEmpty()
        }

        "succeed for sub-package in third-level module" {
            val file = createFile(
                dir = "plugins/package-curation-providers/file/src/main/kotlin/utils",
                content = "package org.ossreviewtoolkit.plugins.packagecurationproviders.file.utils"
            )

            val findings = rule.lint(file.toPath())

            findings should beEmpty()
        }

        "ignore a dash in the module name" {
            val file = createFile(
                dir = "clients/clearly-defined/src/main/kotlin",
                content = "package org.ossreviewtoolkit.clients.clearlydefined"
            )

            val findings = rule.lint(file.toPath())

            findings should beEmpty()
        }

        "fail if an invalid package is used" {
            val file = createFile(
                dir = "model/src/main/kotlin",
                content = "package com.example"
            )

            val findings = rule.lint(file.toPath())

            findings should haveSize(1)
        }

        "fail if the package does not match the module" {
            val file = createFile(
                dir = "model/src/main/kotlin",
                content = "package org.ossreviewtoolkit.cli"
            )

            val findings = rule.lint(file.toPath())

            findings should haveSize(1)
        }

        "fail if the sub-package does not match the directory" {
            val file = createFile(
                dir = "model/src/main/kotlin/config",
                content = "package org.ossreviewtoolkit.model.utils"
            )

            val findings = rule.lint(file.toPath())

            findings should haveSize(1)
        }
    }
})

private fun TestConfiguration.createFile(dir: String, content: String): File {
    // The file needs to be created in the below directory because detekt uses it to determine relative paths in tests.
    val parent = File("build/classes/kotlin/test/$dir").safeMkdirs()

    afterTest {
        parent.safeDeleteRecursively(force = true)
    }

    return File(parent, "Test.kt").apply { writeText(content) }
}
