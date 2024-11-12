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
import java.util.Properties
import java.util.SortedSet

import kotlin.io.path.moveTo

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.PackageManagerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.plugins.packagemanagers.maven.Maven
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.ProcessCapture
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.getCommonParentFile
import org.ossreviewtoolkit.utils.common.searchUpwardsForSubdirectory
import org.ossreviewtoolkit.utils.common.suppressInput
import org.ossreviewtoolkit.utils.ort.JavaBootstrapper
import org.ossreviewtoolkit.utils.ort.createOrtTempDir

import org.semver4j.Semver

// We need at least sbt version 0.13.0 to be able to use "makePom" instead of the deprecated hyphenated
// form "make-pom" and to support declaring Maven-style repositories, see
// http://www.scala-sbt.org/0.13/docs/Publishing.html#Modifying+the+generated+POM.
const val LOWEST_SUPPORTED_SBT_VERSION = "0.13.0"

/**
 * The name of the option to specify the SBT version.
 */
const val OPTION_SBT_VERSION = "sbtVersion"

/**
 * The name of the option to specify the Java version to use.
 */
const val OPTION_JAVA_VERSION = "javaVersion"

/**
 * The name of the option to specify the Java home to use.
 */
const val OPTION_JAVA_HOME = "javaHome"

/**
 * The [SBT](https://www.scala-sbt.org/) package manager for Scala.
 *
 * This package manager supports the following [options][PackageManagerConfiguration.options]:
 * - *sbtVersion*: The version of SBT to use when analyzing projects. Defaults to the version defined in the build
 *   properties.
 * - *javaVersion*: The version of Java to use when analyzing projects. By default, the same Java version as for ORT
 *   itself it used. Overrides `javaHome` if both are specified.
 * - *javaHome*: The directory of the Java home to use when analyzing projects. By default, the same Java home as for
 *   ORT itself is used.
 */
class Sbt(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, "SBT", analysisRoot, analyzerConfig, repoConfig), CommandLineTool {
    class Factory : AbstractPackageManagerFactory<Sbt>("SBT") {
        override val globsForDefinitionFiles = listOf("build.sbt", "build.scala")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Sbt(type, analysisRoot, analyzerConfig, repoConfig)
    }

    override fun command(workingDir: File?) = if (Os.isWindows) "sbt.bat" else "sbt"

    override fun mapDefinitionFiles(definitionFiles: List<File>): List<File> {
        // Some SBT projects do not have a build file in their root, but they still require "sbt" to be run from the
        // project's root directory. In order to determine the root directory, use the common prefix of all
        // definition file paths.
        val workingDir = getCommonParentFile(definitionFiles)

        logger.info { "Determined '$workingDir' as the $managerName project root directory." }

        val sbtVersion = options[OPTION_SBT_VERSION]
        val sbtVersions = getBuildSbtVersions(workingDir)
        when {
            sbtVersion != null -> logger.info { "Using configured custom $managerName version $sbtVersion." }

            sbtVersions.isEmpty() ->
                logger.info { "The build does not configure any $managerName version to be used." }

            sbtVersions.size == 1 ->
                logger.info { "The build configures $managerName version ${sbtVersions.first()} to be used." }

            else ->
                logger.warn { "The build configures multiple different $managerName versions to be used: $sbtVersions" }
        }

        val lowestSbtVersion = sbtVersion?.let { Semver(it) } ?: sbtVersions.firstOrNull() ?: getGlobalSbtVersion()
        require(lowestSbtVersion?.isLowerThan(Semver(LOWEST_SUPPORTED_SBT_VERSION)) != true) {
            "Build $managerName version $lowestSbtVersion is lower than version $LOWEST_SUPPORTED_SBT_VERSION."
        }

        // TODO: Consider auto-detecting the Java version based on the SBT version. See:
        //       https://docs.scala-lang.org/overviews/jdk-compatibility/overview.html#build-tool-compatibility-table
        val javaHome = options[OPTION_JAVA_VERSION]
            ?.takeUnless { JavaBootstrapper.isRunningOnJdk(it) }
            ?.let {
                JavaBootstrapper.installJdk("TEMURIN", it)
                    .onFailure { e -> logger.error { "Failed to bootstrap JDK version $it: ${e.collectMessages()}" } }
                    .getOrNull()
            } ?: options[OPTION_JAVA_HOME]?.let { File(it) }

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
                run(workingDir, *getSbtOptions(sbtVersion, javaHome).toTypedArray(), *command)
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
            val targetDir = workingDir.resolve("target")

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

    private fun getBuildSbtVersions(workingDir: File): SortedSet<Semver> {
        // Determine the SBT version(s) being used.
        val propertiesFiles = workingDir.walkBottomUp().filterTo(mutableListOf()) {
            it.isFile && it.name == "build.properties"
        }

        val versions = sortedSetOf<Semver>()

        propertiesFiles.forEach { file ->
            val props = Properties()
            file.reader().use { props.load(it) }
            props.getProperty("sbt.version")?.let { versions += Semver(it) }
        }

        return versions
    }

    private fun getGlobalSbtVersion(): Semver? {
        // Avoid newer Sbt versions to warn about "Neither build.sbt nor a 'project' directory in the current directory"
        // and prompt the user to continue or quit on Windows where the "-batch" option is not supported.
        val dummyProjectDir = createOrtTempDir(managerName).apply {
            resolve("project").mkdir()
        }

        val process = ProcessCapture(
            command(),
            *DEFAULT_SBT_OPTIONS.toTypedArray(),
            "sbtVersion",
            workingDir = dummyProjectDir
        )

        val versions = process.stdout.lines().mapNotNull { line ->
            VERSION_REGEX.matchEntire(line)?.groupValues?.getOrNull(1)
        }

        return versions.firstOrNull()?.let { Semver(it) }
    }

    override fun resolveDependencies(definitionFiles: List<File>, labels: Map<String, String>) =
        // Simply pass on the list of POM files to Maven, ignoring the SBT build files here.
        Maven(managerName, analysisRoot, analyzerConfig, repoConfig)
            .enableSbtMode()
            .resolveDependencies(definitionFiles, labels)

    override fun resolveDependencies(definitionFile: File, labels: Map<String, String>) =
        // This is not implemented in favor over overriding [resolveDependencies].
        throw NotImplementedError()
}

// See https://github.com/sbt/sbt/blob/v1.5.1/launcher-package/integration-test/src/test/scala/RunnerTest.scala#L9.
private const val SBT_VERSION_PATTERN = "\\d(\\.\\d+){2}(-\\w+)?"

private val VERSION_REGEX = Regex("\\[info]\\s+($SBT_VERSION_PATTERN)")
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
    val targetDirParent = pomFile.parentFile.searchUpwardsForSubdirectory("target")
        ?: return Result.failure(IllegalArgumentException("No target subdirectory found for '$pomFile'."))
    val targetFilename = pomFile.relativeTo(targetDirParent).invariantSeparatorsPath.replace('/', '-')
    val targetFile = targetDirParent.resolve(targetFilename)

    return runCatching { pomFile.toPath().moveTo(targetFile.toPath(), StandardCopyOption.ATOMIC_MOVE).toFile() }
}
