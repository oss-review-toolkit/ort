/*
 * Copyright (C) 2019-2020 HERE Europe B.V.
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

import com.here.ort.analyzer.AbstractPackageManagerFactory
import com.here.ort.analyzer.managers.utils.hasPnpmLockFile
import com.here.ort.analyzer.managers.utils.mapDefinitionFilesForPnpm
import com.here.ort.downloader.VersionControlSystem
import com.here.ort.model.Identifier
import com.here.ort.model.Package
import com.here.ort.model.PackageLinkage
import com.here.ort.model.PackageReference
import com.here.ort.model.VcsInfo
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.model.createAndLogIssue
import com.here.ort.utils.Os
import com.here.ort.utils.log

import com.vdurmont.semver4j.Requirement

import java.io.File

/**
 * The [Performant Node Package Manager](https://pnpm.js.org/) for JavaScript.
 */
open class Pnpm(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : Npm(name, analysisRoot, analyzerConfig, repoConfig) {
    class Factory : AbstractPackageManagerFactory<Pnpm>("PNPM") {
        override val globsForDefinitionFiles = listOf("package.json", "pnpm-lock.yaml")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Pnpm(managerName, analysisRoot, analyzerConfig, repoConfig)
    }

    override fun hasLockFile(projectDir: File) = hasPnpmLockFile(projectDir)

    override fun command(workingDir: File?) = if (Os.isWindows) "pnpm.cmd" else "pnpm"

    override fun getVersionRequirement(): Requirement = Requirement.buildNPM("4.3.* - 4.7.*")

    override fun mapDefinitionFiles(definitionFiles: List<File>) =
        mapDefinitionFilesForPnpm(definitionFiles).toList()

    override fun packageNotInRootModulesDir(packageName: String, rootModulesDir: File,
                                            startModulesDir: File,
                                            packages: Map<String, Package>,
                                            dependencyBranch: List<String>): PackageReference? {
        val altStartModulesDir = startModulesDir.resolve(File(".pnpm")).resolve(File("node_modules"))

        if (!altStartModulesDir.isDirectory) {
            val id = Identifier(managerName, "", packageName, "")

            val issue = createAndLogIssue(
                source = managerName,
                message = "Package '$packageName' was not installed, because the package file could not be found " +
                        "anywhere in '$rootModulesDir'. This might be fine if the module was not installed " +
                        "because it is specific to a different platform."
            )

            return PackageReference(id, PackageLinkage.DYNAMIC, sortedSetOf(), listOf(issue))
        }

        log.debug {
            "Checking for package file for '$packageName' in '$altStartModulesDir'."
        }

        return buildTree(rootModulesDir, altStartModulesDir, packageName, packages, dependencyBranch)
    }

    override fun vcsUpdateFromDirectory(vcsFromPkg: VcsInfo, truePackageDir: File): VcsInfo {
        val vcsExDir = VersionControlSystem.forDirectory(truePackageDir)?.getInfo() ?: VcsInfo.EMPTY

        // Do not use:
        // - revision and resolvedRevision based on directory info
        // - local directory path
        return vcsFromPkg.merge(VcsInfo(vcsExDir.type, vcsExDir.url, "", "", ""))
    }

    /**
     * Install dependencies using the given package manager command.
     */
    override fun installDependencies(workingDir: File) {
        requireLockfile(workingDir) { hasLockFile(workingDir) }

        // Install all PNPM dependencies to enable PNPM to list dependencies.
        if (hasLockFile(workingDir)) {
            run(workingDir, "install", *installParameters)
        } else {
            run(workingDir, "install")
        }

        // TODO: Capture warnings from npm output, e.g. "Unsupported platform" which happens for fsevents on all
        //       platforms except for Mac.
    }
}
