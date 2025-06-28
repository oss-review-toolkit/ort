/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlScalar
import com.charleskorn.kaml.yamlMap

import java.io.File

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.plugins.packagemanagers.node.NodePackageManagerType
import org.ossreviewtoolkit.plugins.packagemanagers.node.parsePackageJson
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.Os

import org.semver4j.range.RangeList
import org.semver4j.range.RangeListFactory

/**
 * The name of Yarn 2+ resource file.
 */
private const val YARN2_RESOURCE_FILE = ".yarnrc.yml"

internal class Yarn2Command(private val corepackEnabled: Boolean?) : CommandLineTool {
    @Suppress("unused") // The no-arg constructor is required by the requirements command.
    constructor() : this(null)

    /**
     * The Yarn 2+ executable is not installed globally: The program shipped by the project in `.yarn/releases` is used
     * instead. The value of the 'yarnPath' property in the resource file `.yarnrc.yml` defines the path to the
     * executable for the current project e.g. `yarnPath: .yarn/releases/yarn-3.2.1.cjs`.
     * This map holds the mapping between the directory and their Yarn 2+ executables. It is only used if Yarn has not
     * been installed via Corepack; then it is accessed under a default name.
     */
    private val yarn2ExecutablesByPath: MutableMap<File, File> = mutableMapOf()

    override fun command(workingDir: File?): String {
        if (workingDir == null) return ""
        if (isCorepackEnabled(workingDir)) return "yarn"

        val executablePath = yarn2ExecutablesByPath.getOrPut(workingDir) { getYarnExecutable(workingDir) }.absolutePath
        return executablePath.takeUnless { Os.isWindows } ?: "node $executablePath"
    }

    override fun getVersion(workingDir: File?): String =
        // `getVersion` with a `null` parameter is called once by the Analyzer to get the version of the global tool.
        // For Yarn2+, the version is specific to each definition file being scanned therefore a global version doesn't
        // apply.
        // TODO: An alternative would be to collate the versions of all tools in `yarn2CommandsByPath`.
        if (workingDir == null) "" else super.getVersion(workingDir)

    override fun getVersionRequirement(): RangeList = RangeListFactory.create(">=2.0.0")

    private fun isCorepackEnabled(workingDir: File): Boolean =
        corepackEnabled ?: isCorepackEnabledInManifest(workingDir)

    private fun getYarnExecutable(workingDir: File): File {
        val yarnrcFile = workingDir.resolve(YARN2_RESOURCE_FILE)
        val yarnConfig = Yaml.default.parseToYamlNode(yarnrcFile.readText()).yamlMap
        val yarnPath = yarnConfig.get<YamlScalar>("yarnPath")?.content

        require(!yarnPath.isNullOrEmpty()) { "No Yarn 2+ executable could be found in '$YARN2_RESOURCE_FILE'." }

        val yarnExecutable = workingDir.resolve(yarnPath)

        // TODO: This is a security risk to blindly run code coming from a repository other than ORT's. ORT
        //       should download the Yarn2 binary from the official repository and run it.
        require(yarnExecutable.isFile) {
            "The Yarn 2+ program '${yarnExecutable.name}' does not exist."
        }

        if (!yarnExecutable.canExecute()) {
            logger.warn {
                "The Yarn 2+ program '${yarnExecutable.name}' should be executable. Changing its rights."
            }

            require(yarnExecutable.setExecutable(true)) {
                "Cannot set the Yarn 2+ program to be executable."
            }
        }

        return yarnExecutable
    }
}

/**
 * Check whether Corepack is enabled based on the `package.json` file in [workingDir]. If no such file is found
 * or if it cannot be read, assume that this is not the case.
 */
private fun isCorepackEnabledInManifest(workingDir: File): Boolean =
    runCatching {
        val packageJson = parsePackageJson(workingDir.resolve(NodePackageManagerType.DEFINITION_FILE))
        !packageJson.packageManager.isNullOrEmpty()
    }.getOrDefault(false)
