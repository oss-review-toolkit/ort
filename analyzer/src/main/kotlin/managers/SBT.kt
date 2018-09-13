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

import ch.frankel.slf4k.*

import com.here.ort.analyzer.PackageManager
import com.here.ort.analyzer.AbstractPackageManagerFactory
import com.here.ort.model.config.AnalyzerConfiguration
import com.here.ort.model.config.RepositoryConfiguration
import com.here.ort.utils.log
import com.here.ort.utils.redirectStderr
import com.here.ort.utils.redirectStdout
import com.here.ort.utils.suppressInput
import com.here.ort.utils.temporaryProperties
import com.here.ort.utils.trapSystemExitCall

import com.vdurmont.semver4j.Requirement
import com.vdurmont.semver4j.Semver

import java.io.File
import java.io.IOException
import java.util.Properties

/**
 * The SBT package manager for Scala, see https://www.scala-sbt.org/.
 */
class SBT(analyzerConfig: AnalyzerConfiguration, repoConfig: RepositoryConfiguration) :
        PackageManager(analyzerConfig, repoConfig) {
    class Factory : AbstractPackageManagerFactory<SBT>() {
        override val globsForDefinitionFiles = listOf("build.sbt", "build.scala")

        override fun create(analyzerConfig: AnalyzerConfiguration, repoConfig: RepositoryConfiguration) =
                SBT(analyzerConfig, repoConfig)
    }

    companion object {
        private val PROJECT_REGEX = Regex("\\[info] \t [ *] (.+)")
        private val POM_REGEX = Regex("\\[info] Wrote (.+\\.pom)")
    }

    private fun checkForSameSbtVersion(versions: List<Semver>): String {
        val uniqueVersions = versions.toSortedSet()
        if (uniqueVersions.size > 1) {
            log.warn { "Different sbt versions used in the same project: $uniqueVersions" }
        }

        return uniqueVersions.first().toString()
    }

    override fun prepareResolution(definitionFiles: List<File>): List<File> {
        if (definitionFiles.isEmpty()) return emptyList()

        val workingDir = if (definitionFiles.count() > 1) {
            // Some SBT projects do not have a build file in their root, but they still require "sbt" to be run from the
            // project's root directory. In order to determine the root directory, use the common prefix of all
            // definition file paths.
            val projectRoot = definitionFiles.map {
                it.absolutePath
            }.reduce { prefix, path ->
                prefix.commonPrefixWith(path)
            }

            File(projectRoot).also {
                log.info { "Determined '$it' as the ${toString()} project root directory." }
            }
        } else {
            definitionFiles.first().parentFile
        }

        // We need at least sbt version 0.13.0 to be able to use "makePom" instead of the deprecated hyphenated
        // form "make-pom" and to support declaring Maven-style repositories, see
        // http://www.scala-sbt.org/0.13/docs/Publishing.html#Modifying+the+generated+POM.
        val sbtVersionRequirement = Requirement.buildIvy("[0.13.0,)")

        // Determine the SBT version(s) being used.
        val rootPropertiesFile = workingDir.resolve("project").resolve("build.properties")
        val propertiesFiles = workingDir.walkBottomUp().filter { it.isFile && it.name == "build.properties" }.toList()

        if (propertiesFiles.contains(rootPropertiesFile)) {
            val versions = mutableListOf<Semver>()

            propertiesFiles.forEach { file ->
                val props = Properties()
                file.reader().use { props.load(it) }
                props.getProperty("sbt.version")?.let { versions += Semver(it) }
            }

            val lowestSbtVersion = checkForSameSbtVersion(versions)
            if (!sbtVersionRequirement.isSatisfiedBy(lowestSbtVersion)) {
                throw IOException("Unsupported ${toString()} version $lowestSbtVersion does not fulfill " +
                        "$sbtVersionRequirement.")
            }
        }

        fun runSBT(vararg command: String): Pair<String, String> {
            var stderr = ""

            val stdout = redirectStdout {
                stderr = redirectStderr {
                    suppressInput {
                        temporaryProperties("sbt.log.noformat" to "true", "user.dir" to workingDir.absolutePath) {
                            trapSystemExitCall {
                                xsbt.boot.Boot.main(command)
                            }
                        }
                    }
                }
            }

            return Pair(stdout, stderr)
        }

        // Get the list of project names.
        val (stdoutProjects, stderrProjects) = runSBT("projects")
        val internalProjectNames = stdoutProjects.lines().mapNotNull {
            PROJECT_REGEX.matchEntire(it)?.groupValues?.getOrNull(1)
        }

        if (internalProjectNames.isEmpty()) {
            log.warn { "No SBT projects found inside the '${workingDir.absolutePath}' directory." }
        }

        log.debug { stderrProjects }

        // Generate the POM files. Note that a single run of makePom might create multiple POM files in case of
        // aggregate projects.
        val makePomCommand = internalProjectNames.joinToString("") { ";$it/makePom" }
        val (stdoutMakePom, stderrMakePom) = runSBT(makePomCommand)
        val pomFiles = stdoutMakePom.lines().mapNotNull { line ->
            POM_REGEX.matchEntire(line)?.groupValues?.getOrNull(1)?.let { File(it) }
        }

        if (pomFiles.isEmpty()) {
            log.warn { "No generated POM files found inside the '${workingDir.absolutePath}' directory." }
        }

        log.debug { stderrMakePom }

        return pomFiles.distinct()
    }

    override fun resolveDependencies(analyzerRoot: File, definitionFiles: List<File>) =
            // Simply pass on the list of POM files to Maven, ignoring the SBT build files here.
            Maven(analyzerConfig, repoConfig)
                    .enableSbtMode()
                    .resolveDependencies(analyzerRoot, prepareResolution(definitionFiles))
}
