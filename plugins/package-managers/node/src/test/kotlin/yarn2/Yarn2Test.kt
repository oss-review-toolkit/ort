/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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
