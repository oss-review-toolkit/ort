/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.sbt

import java.io.File
import java.nio.file.StandardCopyOption

import kotlin.io.path.moveTo

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.analyzer.PackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManagerResult
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.Excludes
import org.ossreviewtoolkit.plugins.api.OrtPlugin
import org.ossreviewtoolkit.plugins.api.PluginDescriptor
import org.ossreviewtoolkit.plugins.packagemanagers.maven.Maven
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.getCommonParentFile
import org.ossreviewtoolkit.utils.common.searchUpwardFor
import org.ossreviewtoolkit.utils.common.suppressInput
import org.ossreviewtoolkit.utils.common.withoutPrefix
import org.ossreviewtoolkit.utils.ort.JavaBootstrapper

import org.semver4j.range.RangeList
import org.semver4j.range.RangeListFactory

internal object SbtCommand : CommandLineTool {
    override fun command(workingDir: File?) = if (Os.isWindows) "sbt.bat" else "sbt"

    // Require at least version 1.3.3 which adds "--version" to SBT launcher scripts.
    override fun getVersionRequirement(): RangeList = RangeListFactory.create(">=1.3.3")

    override fun transformVersion(output: String): String {
        val lines = output.lines()

        // See the expected test output here:
        // https://github.com/sbt/sbt-launcher-package/pull/285/files#diff-25311dcba3a7ac9d113cb964bd15cff334450620b35fdd1361a45b9bf249c10eR102
        // https://github.com/sbt/sbt-launcher-package/pull/288/files#diff-25311dcba3a7ac9d113cb964bd15cff334450620b35fdd1361a45b9bf249c10eR91
        val projectVersion = lines.firstNotNullOfOrNull { it.withoutPrefix("sbt version in this project: ") }
        val scriptVersion = lines.firstNotNullOfOrNull { it.withoutPrefix("sbt script version: ") }

        return checkNotNull(projectVersion ?: scriptVersion)
    }
}

data class SbtConfig(
    /**
     * The version of SBT to use when analyzing projects. Defaults to the version defined in the build properties.
     */
    val sbtVersion: String?,

    /**
     * The version of Java to use when analyzing projects. By default, the same Java version as for ORT itself it used.
     * Overrides `javaHome` if both are specified.
     */
    val javaVersion: String?,

    /**
     * The directory of the Java home to use when analyzing projects. By default, the same Java home as for ORT itself
     * is used.
     */
    val javaHome: String?
)

/**
 * The [SBT](https://www.scala-sbt.org/) package manager for Scala.
 */
@OrtPlugin(
    id = "SBT",
    displayName = "SBT",
    description = "The SBT package manager for Scala.",
    factory = PackageManagerFactory::class
)
class Sbt(override val descriptor: PluginDescriptor = SbtFactory.descriptor, private val config: SbtConfig) :
    PackageManager("SBT") {
    override val globsForDefinitionFiles = listOf("build.sbt", "build.scala")

    override fun mapDefinitionFiles(
        analysisRoot: File,
        definitionFiles: List<File>,
        analyzerConfig: AnalyzerConfiguration
    ): List<File> {
        // Some SBT projects do not have a build file in their root, but they still require "sbt" to be run from the
        // project's root directory. In order to determine the root directory, use the common prefix of all
        // definition file paths.
        val workingDir = getCommonParentFile(definitionFiles)

        logger.info { "Determined '$workingDir' as the $projectType project root directory." }

        // TODO: Consider auto-detecting the Java version based on the SBT version. See:
        //       https://docs.scala-lang.org/overviews/jdk-compatibility/overview.html#build-tool-compatibility-table
        val javaHome = config.javaVersion
            ?.takeUnless { JavaBootstrapper.isRunningOnJdk(it) }
            ?.let {
                JavaBootstrapper.installJdk("TEMURIN", it)
                    .onFailure { e -> logger.error { "Failed to bootstrap JDK version $it: ${e.collectMessages()}" } }
                    .getOrNull()
            } ?: config.javaHome?.let { File(it) }

        javaHome?.also {
            logger.info { "Setting Java home for project analysis to '$it'." }
        }

        fun getSbtOptions(sbtVersion: String?, javaHome: File?): List<String> =
            buildList {
                addAll(DEFAULT_SBT_OPTIONS)

                sbtVersion?.also {
                    add("--sbt-version")
                    add(it)
                }

                javaHome?.also {
                    add("--java-home")
                    add(it.absolutePath)
                }
            }

        fun runSbt(vararg command: String) =
            suppressInput {
                SbtCommand.run(workingDir, *getSbtOptions(config.sbtVersion, javaHome).toTypedArray(), *command)
                    .requireSuccess()
            }

        // Get the list of project names.
        val internalProjectNames = runSbt("projects").stdout.lines().mapNotNull {
            PROJECT_REGEX.matchEntire(it)?.groupValues?.getOrNull(1)
        }

        if (internalProjectNames.isEmpty()) {
            logger.warn { "No SBT project found inside the '$workingDir' directory." }
        }

        // Generate the POM files. Note that a single run of makePom might create multiple POM files in case of
        // aggregate projects.
        val pomFiles = mutableListOf<File>()

        val makePomCommand = internalProjectNames.joinToString("") { ";$it/makePom" }
        runSbt(makePomCommand).stdout.lines().mapNotNullTo(pomFiles) { line ->
            POM_REGEX.matchEntire(line)?.groupValues?.getOrNull(1)?.let { File(it) }
        }

        if (pomFiles.isEmpty()) {
            val targetDir = workingDir / "target"

            logger.info {
                "No POM locations found in the output of SBT's 'makePom' command. Falling back to look for POMs in " +
                    "the '$targetDir' directory."
            }

            targetDir.walk().maxDepth(1).filterTo(pomFiles) { it.isFile && it.extension == "pom" }
        }

        return pomFiles.distinct().map { pomFile ->
            moveGeneratedPom(pomFile).onFailure {
                logger.error { "Moving the POM file failed: ${it.message}" }
            }.getOrDefault(pomFile)
        }
    }

    override fun resolveDependencies(
        analysisRoot: File,
        definitionFiles: List<File>,
        excludes: Excludes,
        analyzerConfig: AnalyzerConfiguration,
        labels: Map<String, String>
    ): PackageManagerResult {
        return Maven(sbtMode = true).run {
            beforeResolution(analysisRoot, definitionFiles, analyzerConfig)

            // Simply pass on the list of POM files to Maven, ignoring the SBT build files here.
            resolveDependencies(analysisRoot, definitionFiles, excludes, analyzerConfig, labels).also {
                afterResolution(analysisRoot, definitionFiles)
            }
        }
    }

    override fun resolveDependencies(
        analysisRoot: File,
        definitionFile: File,
        excludes: Excludes,
        analyzerConfig: AnalyzerConfiguration,
        labels: Map<String, String>
    ) = throw NotImplementedError() // This is not implemented in favor of overriding [resolveDependencies].
}

private val PROJECT_REGEX = Regex("\\[info] \t [ *] (\\S+)")
private val POM_REGEX = Regex("\\[info] Wrote (.+\\.pom)")

private val DEFAULT_SBT_OPTIONS = listOfNotNull(
    // Batch mode (which suppresses interactive prompts) is not supported on Windows, compare
    // https://github.com/sbt/sbt-launcher-package/blob/25c1b96/src/universal/bin/sbt.bat#L861 with
    // https://github.com/sbt/sbt-launcher-package/blob/25c1b96/src/universal/bin/sbt#L449.
    "-batch".takeUnless { Os.isWindows },

    // Enable the CI mode which disables console colors and supershell, see
    // https://www.scala-sbt.org/1.x/docs/Command-Line-Reference.html#Command+Line+Options.
    "-Dsbt.ci=true",

    // Disable console colors explicitly as in some cases CI_MODE is not enough.
    "-Dsbt.color=false",

    // Disable the JLine terminal. Without this the JLine terminal can occasionally send a signal that causes the
    // parent process to suspend, for example IntelliJ can be suspended while running the SbtTest.
    "-Djline.terminal=none",

    // Set the correct user home dorectory in some Docker scenarios.
    "-Duser.home=${Os.userHomeDirectory}"
).map {
    if (Os.isWindows) "\"$it\"" else it
}

private fun moveGeneratedPom(pomFile: File): Result<File> {
    val targetDirParent = pomFile.parentFile.searchUpwardFor(dirPath = "target")
        ?: return Result.failure(IllegalArgumentException("No target subdirectory found for '$pomFile'."))
    val targetFilename = pomFile.relativeTo(targetDirParent).invariantSeparatorsPath.replace('/', '-')
    val targetFile = targetDirParent / targetFilename

    return runCatching { pomFile.toPath().moveTo(targetFile.toPath(), StandardCopyOption.ATOMIC_MOVE).toFile() }
}
