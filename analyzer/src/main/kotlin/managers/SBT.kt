/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

import ch.frankel.slf4k.*

import com.here.ort.analyzer.Main
import com.here.ort.analyzer.PackageManager
import com.here.ort.analyzer.PackageManagerFactory
import com.here.ort.utils.OS
import com.here.ort.utils.ProcessCapture
import com.here.ort.utils.checkCommandVersion
import com.here.ort.utils.log

import com.vdurmont.semver4j.Requirement
import com.vdurmont.semver4j.Semver

import java.io.File
import java.io.IOException

class SBT : PackageManager() {
    companion object : PackageManagerFactory<SBT>(
            "http://www.scala-sbt.org/",
            "Scala",
            listOf("build.sbt", "build.scala")
    ) {
        private val VERSION_REGEX = Regex("\\[info]\\s+(\\d+\\.\\d+\\.[^\\s]+)")
        private val POM_REGEX = Regex("\\[info] Wrote (.+\\.pom)")

        // Batch mode (which suppresses interactive prompts) is only supported on non-Windows, see
        // https://github.com/sbt/sbt-launcher-package/blob/d251388/src/universal/bin/sbt#L86.
        private val SBT_BATCH_MODE = if (!OS.isWindows) "-batch" else ""

        // See https://github.com/sbt/sbt/issues/2695.
        private val SBT_LOG_NO_FORMAT = "-Dsbt.log.noformat=true".let {
            if (OS.isWindows) {
                "\"$it\""
            } else {
                it
            }
        }

        override fun create() = SBT()
    }

    override fun command(workingDir: File) = if (OS.isWindows) "sbt.bat" else "sbt"

    override fun toString() = SBT.toString()

    private fun extractLowestSbtVersion(stdout: String): String {
        val versions = stdout.lines().mapNotNull {
            VERSION_REGEX.matchEntire(it)?.groupValues?.getOrNull(1)?.let { Semver(it) }
        }

        val uniqueVersions = versions.toSortedSet()
        if (uniqueVersions.size > 1) {
            log.info { "Different sbt versions used in the same project: $uniqueVersions" }
        }

        return uniqueVersions.first().toString()
    }

    override fun prepareResolution(definitionFiles: List<File>): List<File> {
        if (definitionFiles.isEmpty()) return emptyList()

        // Note that "sbt sbtVersion" behaves differently when executed inside or outside an SBT project, see
        // https://stackoverflow.com/a/20337575/1127485.
        var workingDir = definitionFiles.first().parentFile

        // We need at least sbt version 0.13.0 to be able to use "makePom" instead of the deprecated hyphenated form
        // "make-pom" and to support declaring Maven-style repositories, see
        // http://www.scala-sbt.org/0.13/docs/Publishing.html#Modifying+the+generated+POM.
        checkCommandVersion(
                command(workingDir),
                Requirement.buildIvy("[0.13.0,)"),
                versionArguments = "$SBT_BATCH_MODE $SBT_LOG_NO_FORMAT sbtVersion",
                workingDir = workingDir,
                ignoreActualVersion = Main.ignoreVersions,
                transform = this::extractLowestSbtVersion
        )

        val pomFiles = sortedSetOf<File>()

        if (definitionFiles.count() > 1) {
            // Some SBT projects do not have a build file in their root, but they still require "sbt" to be run from the
            // project's root directory. In order to determine the root directory, use the common prefix of all
            // definition file paths.
            val projectRoot = definitionFiles.map {
                it.absolutePath
            }.reduce { prefix, path ->
                prefix.commonPrefixWith(path)
            }

            workingDir = File(projectRoot)

            val sbt = ProcessCapture(workingDir, command(workingDir), SBT_BATCH_MODE, SBT_LOG_NO_FORMAT, "makePom")
            if (sbt.isSuccess()) {
                // Get the list of POM files created by parsing stdout. A single call might create multiple POM
                // files in case of sub-projects.
                sbt.stdout().lines().mapNotNullTo(pomFiles) {
                    POM_REGEX.matchEntire(it)?.groupValues?.getOrNull(1)?.let { File(it) }
                }
            } else {
                log.warn {
                    "Running '${sbt.commandLine}' in the determined project root directory '$workingDir' failed. " +
                            "This might be acceptable if this is not actually the root of an SBT project."
                }
            }
        }

        definitionFiles.forEach { definitionFile ->
            workingDir = definitionFile.parentFile

            // Check if a POM was already generated if this is a sub-project in a multi-project.
            val targetDir = File(workingDir, "target")
            val hasPom = targetDir.isDirectory && targetDir.walkTopDown().filter {
                it.isFile && it.extension == "pom"
            }.any()

            if (!hasPom) {
                val sbt = ProcessCapture(workingDir, command(workingDir), SBT_BATCH_MODE, SBT_LOG_NO_FORMAT, "makePom")
                if (sbt.isSuccess()) {
                    // Get the list of POM files created by parsing stdout. A single call might create multiple POM
                    // files in case of sub-projects.
                    sbt.stdout().lines().mapNotNullTo(pomFiles) {
                        POM_REGEX.matchEntire(it)?.groupValues?.getOrNull(1)?.let { File(it) }
                    }
                } else {
                    if (pomFiles.isEmpty()) {
                        throw IOException(sbt.errorMessage)
                    } else {
                        log.warn {
                            "A subsequent run of '${sbt.commandLine}' in directory '$workingDir' failed. " +
                                    "This might be acceptable if this is a sub-project in a multi-project " +
                                    "that does not publish any artifacts."
                        }
                    }
                }
            }
        }

        return pomFiles.toList()
    }

    override fun resolveDependencies(analyzerRoot: File, definitionFiles: List<File>) =
            // Simply pass on the list of POM files to Maven, ignoring the SBT build files here.
            Maven.create().enableSbtMode().resolveDependencies(analyzerRoot, prepareResolution(definitionFiles))
}
