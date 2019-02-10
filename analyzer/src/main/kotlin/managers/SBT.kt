/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

import com.here.ort.analyzer.AbstractPackageManagerFactory
import com.here.ort.analyzer.PackageManager
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.utils.CommandLineTool
import com.here.ort.utils.OS
import com.here.ort.utils.getCommonFilePrefix
import com.here.ort.utils.log
import com.here.ort.utils.suppressInput

import com.vdurmont.semver4j.Requirement
import com.vdurmont.semver4j.Semver

import java.io.File
import java.io.IOException
import java.util.Properties

/**
 * The SBT package manager for Scala, see https://www.scala-sbt.org/.
 */
class SBT(name: String, analyzerConfig: AnalyzerConfiguration, repoConfig: RepositoryConfiguration) :
        PackageManager(name, analyzerConfig, repoConfig), CommandLineTool {
    companion object {
        private val VERSION_REGEX = Regex("\\[info]\\s+(\\d+\\.\\d+\\.[^\\s]+)")
        private val PROJECT_REGEX = Regex("\\[info] \t [ *] (.+)")
        private val POM_REGEX = Regex("\\[info] Wrote (.+\\.pom)")

        // Batch mode (which suppresses interactive prompts) is only supported on non-Windows, see
        // https://github.com/sbt/sbt-launcher-package/blob/d251388/src/universal/bin/sbt#L86.
        private val BATCH_MODE = if (!OS.isWindows) "-batch" else ""

        // See https://github.com/sbt/sbt/issues/2695.
        private val LOG_NO_FORMAT = "-Dsbt.log.noformat=true".let {
            if (OS.isWindows) {
                "\"$it\""
            } else {
                it
            }
        }
    }

    class Factory : AbstractPackageManagerFactory<SBT>("SBT") {
        override val globsForDefinitionFiles = listOf("build.sbt", "build.scala")

        override fun create(analyzerConfig: AnalyzerConfiguration, repoConfig: RepositoryConfiguration) =
                SBT(managerName, analyzerConfig, repoConfig)
    }

    override fun command(workingDir: File?) = if (OS.isWindows) "sbt.bat" else "sbt"

    override fun getVersionRequirement(): Requirement =
            // We need at least sbt version 0.13.0 to be able to use "makePom" instead of the deprecated hyphenated
            // form "make-pom" and to support declaring Maven-style repositories, see
            // http://www.scala-sbt.org/0.13/docs/Publishing.html#Modifying+the+generated+POM.
            Requirement.buildIvy("[0.13.0,)")

    private fun extractLowestSbtVersion(stdout: String): String {
        val versions = stdout.lines().mapNotNull { line ->
            VERSION_REGEX.matchEntire(line)?.groupValues?.getOrNull(1)?.let { Semver(it) }
        }

        return checkForSameSbtVersion(versions)
    }

    private fun checkForSameSbtVersion(versions: List<Semver>): String {
        val uniqueVersions = versions.toSortedSet()
        if (uniqueVersions.size > 1) {
            log.warn { "Different sbt versions used in the same project: $uniqueVersions" }
        }

        return uniqueVersions.first().toString()
    }

    override fun mapDefinitionFiles(definitionFiles: List<File>): List<File> {
        val workingDir = if (definitionFiles.count() > 1) {
            // Some SBT projects do not have a build file in their root, but they still require "sbt" to be run from the
            // project's root directory. In order to determine the root directory, use the common prefix of all
            // definition file paths.
            getCommonFilePrefix(definitionFiles).also {
                log.info { "Determined '$it' as the $managerName project root directory." }
            }
        } else {
            definitionFiles.first().parentFile
        }

        fun runSBT(vararg command: String) =
                suppressInput {
                    run(workingDir, BATCH_MODE, LOG_NO_FORMAT, *command)
                }

        // Get the list of project names.
        val internalProjectNames = runSBT("projects").stdout.lines().mapNotNull {
            PROJECT_REGEX.matchEntire(it)?.groupValues?.getOrNull(1)
        }

        if (internalProjectNames.isEmpty()) {
            log.warn { "No SBT projects found inside the '${workingDir.absolutePath}' directory." }
        }

        // Generate the POM files. Note that a single run of makePom might create multiple POM files in case of
        // aggregate projects.
        val makePomCommand = internalProjectNames.joinToString("") { ";$it/makePom" }
        val pomFiles = runSBT(makePomCommand).stdout.lines().mapNotNull { line ->
            POM_REGEX.matchEntire(line)?.groupValues?.getOrNull(1)?.let { File(it) }
        }

        if (pomFiles.isEmpty()) {
            log.warn { "No generated POM files found inside the '${workingDir.absolutePath}' directory." }
        }

        return pomFiles.distinct()
    }

    override fun prepareResolution(definitionFiles: List<File>) {
        val workingDir = if (definitionFiles.count() > 1) {
            // Some SBT projects do not have a build file in their root, but they still require "sbt" to be run from the
            // project's root directory. In order to determine the root directory, use the common prefix of all
            // definition file paths.
            getCommonFilePrefix(definitionFiles).also {
                log.info { "Determined '$it' as the $managerName project root directory." }
            }
        } else {
            definitionFiles.first().parentFile
        }

        // Determine the SBT version(s) being used.
        val rootPropertiesFile = workingDir.resolve("project").resolve("build.properties")
        val propertiesFiles = workingDir.walkBottomUp().filter { it.isFile && it.name == "build.properties" }.toList()

        if (!propertiesFiles.contains(rootPropertiesFile)) {
            // Note that "sbt sbtVersion" behaves differently when executed inside or outside an SBT project, see
            // https://stackoverflow.com/a/20337575/1127485.
            checkVersion(
                    versionArguments = "$BATCH_MODE $LOG_NO_FORMAT sbtVersion",
                    workingDir = workingDir,
                    ignoreActualVersion = analyzerConfig.ignoreToolVersions,
                    transform = this::extractLowestSbtVersion
            )
        } else {
            val versions = mutableListOf<Semver>()

            propertiesFiles.forEach { file ->
                val props = Properties()
                file.reader().use { props.load(it) }
                props.getProperty("sbt.version")?.let { versions += Semver(it) }
            }

            val sbtVersionRequirement = getVersionRequirement()
            val lowestSbtVersion = checkForSameSbtVersion(versions)

            if (!sbtVersionRequirement.isSatisfiedBy(lowestSbtVersion)) {
                throw IOException("Unsupported $managerName version $lowestSbtVersion does not fulfill " +
                        "$sbtVersionRequirement.")
            }
        }
    }

    override fun resolveDependencies(analyzerRoot: File, definitionFiles: List<File>) =
            // Simply pass on the list of POM files to Maven, ignoring the SBT build files here.
            Maven(managerName, analyzerConfig, repoConfig)
                    .enableSbtMode()
                    .resolveDependencies(analyzerRoot, definitionFiles)

    override fun resolveDependencies(definitionFile: File) =
            // This is not implemented in favor over overriding [resolveDependencies].
            throw NotImplementedError()
}
