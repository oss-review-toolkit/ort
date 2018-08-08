/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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

package com.here.ort.analyzer.managers

import com.here.ort.analyzer.PackageManagerFactory
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.utils.OS
import com.here.ort.utils.checkCommandVersion

import com.vdurmont.semver4j.Requirement

import java.io.File

class Yarn(config: AnalyzerConfiguration) : NPM(config) {
    companion object : PackageManagerFactory<Yarn>(
            "https://www.yarnpkg.com/",
            "JavaScript",
            listOf("yarn.lock")
    ) {
        override fun create(config: AnalyzerConfiguration) = Yarn(config)
    }

    override val recognizedLockFiles = listOf("yarn.lock")

    override fun command(workingDir: File) = if (OS.isWindows) { "yarn.cmd" } else { "yarn" }

    override fun toString() = Yarn.toString()

    override fun prepareResolution(definitionFiles: List<File>): List<File> {
        val workingDir = definitionFiles.first().parentFile

        // We do not actually depend on any features specific to a Yarn version, but we still want to stick to a fixed
        // minor version to be sure to get consistent results.
        checkCommandVersion(command(workingDir), Requirement.buildIvy("1.3.+"),
                ignoreActualVersion = config.ignoreToolVersions)

        return definitionFiles
    }
}
