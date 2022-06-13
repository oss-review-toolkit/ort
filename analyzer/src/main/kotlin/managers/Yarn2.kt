/*
 * Copyright (C) 2022 Bosch.IO GmbH
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

package org.ossreviewtoolkit.analyzer.managers

import com.fasterxml.jackson.databind.JsonNode

import com.vdurmont.semver4j.Requirement

import java.io.File

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.managers.utils.hasYarnLockFile
import org.ossreviewtoolkit.analyzer.managers.utils.mapDefinitionFilesForYarn2
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.model.jsonMapper
import org.ossreviewtoolkit.model.yamlMapper
import org.ossreviewtoolkit.utils.common.ProcessCapture
import org.ossreviewtoolkit.utils.ort.log

/**
 * The [Yarn 2+](https://next.yarnpkg.com/) package manager for JavaScript.
 */
class Yarn2(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : Npm(name, analysisRoot, analyzerConfig, repoConfig) {
    class Factory : AbstractPackageManagerFactory<Yarn2>("Yarn2") {
        override val globsForDefinitionFiles = listOf("package.json")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Yarn2(managerName, analysisRoot, analyzerConfig, repoConfig)
    }

    /**
     * The Yarn 2+ executable is not installed globally: The program shipped by the project in `.yarn/releases` is used
     * instead. The value of the 'yarnPath' property in the resource file `.yarnrc.yml` defines the path to the
     * executable for the current project e.g. `yarnPath: .yarn/releases/yarn-3.2.1.cjs`.
     * This map holds the mapping between the directory and their Yarn 2+ executables.
     */
    private val yarn2ExecutablesByPath: MutableMap<File, String> = mutableMapOf()

    /**
     * A NPM package manager, used to call NPM when Yarn 2+ does not provide enough information.
     */
    private val delegateNpmAnalyzer = Npm("npm", analysisRoot, analyzerConfig, repoConfig)

    companion object {
        // The name of Yarn 2+ resource file.
        const val YARN2_RESOURCE_FILE = ".yarnrc.yml"

        // The property in `.yarnrc.yml`containing the path to the Yarn2+ executable.
        const val YARN_PATH_PROPERTY_NAME = "yarnPath"
    }

    override fun hasLockFile(projectDir: File) = hasYarnLockFile(projectDir)

    override fun command(workingDir: File?): String {
        if (workingDir == null) return ""

        return yarn2ExecutablesByPath.getOrPut(workingDir) {
            val yarnConfig = yamlMapper.readTree(workingDir.resolve(YARN2_RESOURCE_FILE))
            val yarnCommand = requireNotNull(yarnConfig[YARN_PATH_PROPERTY_NAME]) {
                "No Yarn 2+ executable could be found in 'yarnrc.yml'."
            }

            val yarnExecutable = workingDir.resolve(yarnCommand.textValue())

            // TODO: Yarn2 executable is a `cjs` file. Check if under Windows it needs to be run with `node`.

            // TODO: This is a security risk to blindly run code coming from a repository other than ORT's. ORT
            //       should download the Yarn2 binary from the official repository and run it.
            require(yarnExecutable.isFile) {
                "The Yarn 2+ program '${yarnExecutable.name}' does not exist."
            }

            if (!yarnExecutable.canExecute()) {
                log.warn {
                    "The Yarn 2+ program '${yarnExecutable.name}' should be executable. Changing its rights."
                }

                require(yarnExecutable.setExecutable(true)) {
                    "Cannot set the Yarn 2+ program to be executable."
                }
            }

            yarnExecutable.absolutePath
        }
    }

    override fun getVersion(workingDir: File?): String =
        // `getVersion` with a `null` parameter is called once by the Analyzer to get the version of the global tool.
        // For Yarn2+, the version is specific to each definition file being scanned therefore a global version doesn't
        // apply.
        // TODO: An alternative would be to collate the versions of all tools in `yarn2CommandsByPath`.
        if (workingDir == null) "" else super.getVersion(workingDir)

    override fun getVersionRequirement(): Requirement = Requirement.buildNPM(">=2.0.0")

    override fun mapDefinitionFiles(definitionFiles: List<File>) = mapDefinitionFilesForYarn2(definitionFiles).toList()

    override fun beforeResolution(definitionFiles: List<File>) =
        // We depend on a version >= 2, so we check the version for safety.
        definitionFiles.forEach { checkVersion(it.parentFile) }

    override fun runInstall(workingDir: File): ProcessCapture =
        // It should be enough for Yarn2+ to parse the lockfile to get the dependencies, and, if the former doesn't
        // exist, run `yarn install --mode update-lockfile` to generate it (since yarn version 3.0.0-rc.10).
        // Unfortunately, the lockfile doesn't contain the information required to build ORT's packages. An alternative
        // could be to run `npm show --json` on each package present in the lockfile and rebuild a node_modules
        // file hierarchy to be able to reuse the code present in the Npm package manager.
        // Fortunately, an easier solution exist: by setting the option `nodeLinker` in the `yarnrc` file or as an
        // environment variable, one can instruct Yarn to generate the node_modules files hierarchy.
        run("install", workingDir = workingDir, environment = mapOf("YARN_NODE_LINKER" to "node-modules"))

    override fun getRemotePackageDetails(workingDir: File, packageName: String): JsonNode {
        // Yarn2+ 'info' command does not provide enough information so the default behavior of 'npm view --json' is
        // kept. Note that it is mandatory to override this function as the super function calls `run()` which would
        // call Yarn.
        val process = delegateNpmAnalyzer.run(workingDir, "view", "--json", packageName)
        return jsonMapper.readTree(process.stdout)
    }
}
