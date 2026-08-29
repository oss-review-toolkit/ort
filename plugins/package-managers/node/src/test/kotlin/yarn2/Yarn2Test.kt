/*
 * Copyright (C) 2024 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.node.yarn2

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import java.io.File

import org.ossreviewtoolkit.utils.common.Os

class Yarn2Test : WordSpec({
    "command" should {
        "return the executable defined in .yarnrc.yml if no package.json is present" {
            checkExecutableFromYarnRc(tempdir())
        }

        "return the executable defined in .yarnrc.yml if no package manager is defined" {
            val workingDir = tempdir()
            writePackageJson(workingDir, null)

            checkExecutableFromYarnRc(workingDir)
        }

        "return the executable defined in .yarnrc.yml if package.json is invalid" {
            val workingDir = tempdir()
            workingDir.resolve("package.json").writeText("invalid-json")

            checkExecutableFromYarnRc(workingDir)
        }

        "throw if no executable is defined in .yarnrc.yml" {
            val workingDir = tempdir()
            workingDir.resolve(".yarnrc.yml").writeText("someProperty: some-value")

            val exception = shouldThrow<IllegalArgumentException> {
                Yarn2Command().command(workingDir)
            }

            exception.localizedMessage shouldContain "No Yarn 2+ executable"
        }

        "throw if the executable defined in .yarnrc.yml does not exist" {
            val workingDir = tempdir()
            val executable = "non-existing-yarn-wrapper.js"
            workingDir.resolve(".yarnrc.yml").writeText("yarnPath: $executable")

            val exception = shouldThrow<IllegalArgumentException> {
                Yarn2Command().command(workingDir)
            }

            exception.localizedMessage shouldContain executable
        }

        "return the default executable name if Corepack is enabled based on the configuration option" {
            val workingDir = tempdir()

            val yarn = Yarn2Factory.create(corepackEnabled = true)
            val command = yarn.yarn2Command.command(workingDir)

            command shouldBe "yarn"
        }

        "return the default executable name if Corepack is enabled based on the package.json" {
            val workingDir = tempdir()
            writePackageJson(workingDir, "yarn@4.0.0")

            val command = Yarn2Command().command(workingDir)

            command shouldBe "yarn"
        }

        "return the executable defined in .yarnrc.yml if Corepack detection is turned off" {
            val workingDir = tempdir()
            writePackageJson(workingDir, "yarn@4.0.0")

            checkExecutableFromYarnRc(workingDir, corepackEnabled = false)
        }
    }

    "Dependency.realLocator" should {
        "return the locator unchanged if it does not refer to a virtual package" {
            val dep = PackageInfo.Dependency(
                descriptor = "lodash@npm:4.17.21",
                locator = "lodash@npm:4.17.21"
            )

            dep.realLocator shouldBe "lodash@npm:4.17.21"
        }

        "return the real locator for a scoped virtual package" {
            val dep = PackageInfo.Dependency(
                descriptor = "@scope/pkg@npm:1.2.3",
                locator = "@scope/pkg@virtual:abc123#npm:1.2.3"
            )

            dep.realLocator shouldBe "@scope/pkg@npm:1.2.3"
        }

        "return the real locator for a virtual package without a scope" {
            val dep = PackageInfo.Dependency(
                descriptor = "pkg@npm:1.2.3",
                locator = "pkg@virtual:abc123#npm:1.2.3"
            )

            dep.realLocator shouldBe "pkg@npm:1.2.3"
        }

        "return the real locator for a virtual package with a long hash" {
            val dep = PackageInfo.Dependency(
                descriptor = "some-package@npm:2.0.0",
                locator = "some-package@virtual:1a2b3c4d5e6f7890abcdef#npm:2.0.0"
            )

            dep.realLocator shouldBe "some-package@npm:2.0.0"
        }

        "return the real locator for a scoped virtual package with a long hash" {
            val dep = PackageInfo.Dependency(
                descriptor = "@org/utils@npm:0.9.1",
                locator = "@org/utils@virtual:1a2b3c4d5e6f7890abcdef#npm:0.9.1"
            )

            dep.realLocator shouldBe "@org/utils@npm:0.9.1"
        }

        "return the locator unchanged if there is no segment between '@' and '#npm:'" {
            // A locator like "pkg@#npm:1.0.0" is not a valid virtual package locator because the middle
            // segment (e.g. "virtual:<hash>") is missing. The regex must not match in this case.
            val dep = PackageInfo.Dependency(
                descriptor = "pkg@npm:1.0.0",
                locator = "pkg@#npm:1.0.0"
            )

            dep.realLocator shouldBe "pkg@#npm:1.0.0"
        }
    }
})

/**
 * Check whether an executable defined in a `.yarnrc.yml` file is used when invoked with the given [workingDir]
 * and [config]. This should be the case when Corepack is not enabled.
 */
private fun checkExecutableFromYarnRc(workingDir: File, corepackEnabled: Boolean? = null) {
    val executable = "yarn-wrapper.js"

    workingDir.resolve(".yarnrc.yml").writeText("yarnPath: $executable")

    val executableFile = workingDir.resolve(executable).apply {
        writeText("#!/usr/bin/env node\nconsole.log('yarn')")
    }

    val yarn = Yarn2Factory.create(corepackEnabled = corepackEnabled)
    val command = yarn.yarn2Command.command(workingDir)

    if (Os.isWindows) {
        command shouldBe "node ${executableFile.absolutePath}"
    } else {
        command shouldBe executableFile.absolutePath
    }
}

/**
 * Write a `package.json` file to [dir] with some default properties and an optional [packageManager] entry.
 */
private fun writePackageJson(dir: File, packageManager: String?) {
    val packageManagerProperty = packageManager?.let { """"packageManager": "$it"""" }.orEmpty()
    dir.resolve("package.json").writeText(
        """
        {
            "name": "test",
            "version": "1.0.0",
            $packageManagerProperty
        }
        """.trimIndent()
    )
}
