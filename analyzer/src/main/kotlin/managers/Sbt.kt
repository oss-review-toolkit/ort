/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

import com.vdurmont.semver4j.Requirement
import com.vdurmont.semver4j.Semver

import java.io.File
import java.io.IOException
import java.nio.file.StandardCopyOption
import java.util.Properties

import kotlin.io.path.moveTo

import org.ossreviewtoolkit.analyzer.AbstractPackageManagerFactory
import org.ossreviewtoolkit.analyzer.PackageManager
import org.ossreviewtoolkit.model.config.AnalyzerConfiguration
import org.ossreviewtoolkit.model.config.RepositoryConfiguration
import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.Os
import org.ossreviewtoolkit.utils.common.getCommonFileParent
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.common.searchUpwardsForSubdirectory
import org.ossreviewtoolkit.utils.common.suppressInput
import org.ossreviewtoolkit.utils.ort.createOrtTempDir
import org.ossreviewtoolkit.utils.ort.log

/**
 * The [SBT](https://www.scala-sbt.org/) package manager for Scala.
 */
class Sbt(
    name: String,
    analysisRoot: File,
    analyzerConfig: AnalyzerConfiguration,
    repoConfig: RepositoryConfiguration
) : PackageManager(name, analysisRoot, analyzerConfig, repoConfig), CommandLineTool {
    companion object {
        // See https://github.com/sbt/sbt/blob/v1.5.1/launcher-package/integration-test/src/test/scala/RunnerTest.scala#L9.
        private const val SBT_VERSION_PATTERN = "\\d(\\.\\d+){2}(-\\w+)?"

        private val VERSION_REGEX = Regex("\\[info]\\s+($SBT_VERSION_PATTERN)")
        private val PROJECT_REGEX = Regex("\\[info] \t [ *] (.+)")
        private val POM_REGEX = Regex("\\[info] Wrote (.+\\.pom)")

        // Batch mode (which suppresses interactive prompts) is not supported on Windows, compare
        // https://github.com/sbt/sbt-launcher-package/blob/25c1b96/src/universal/bin/sbt.bat#L861 with
        // https://github.com/sbt/sbt-launcher-package/blob/25c1b96/src/universal/bin/sbt#L449.
        private val BATCH_MODE = "-batch".takeUnless { Os.isWindows }.orEmpty()

        // Enable the CI mode which disables console colors and supershell, see
        // https://www.scala-sbt.org/1.x/docs/Command-Line-Reference.html#Command+Line+Options.
        private val CI_MODE = "-Dsbt.ci=true".addQuotesOnWindows()

        // Disable console colors explicitly as in some cases CI_MODE is not enough.
        private val NO_COLOR = "-Dsbt.color=false".addQuotesOnWindows()

        // Disable the JLine terminal. Without this the JLine terminal can occasionally send a signal that causes the
        // parent process to suspend, for example IntelliJ can be suspended while running the SbtTest.
        private val DISABLE_JLINE = "-Djline.terminal=none".addQuotesOnWindows()

        private val SBT_OPTIONS = arrayOf(BATCH_MODE, CI_MODE, NO_COLOR, DISABLE_JLINE)

        private fun String.addQuotesOnWindows() = if (Os.isWindows) "\"$this\"" else this
    }

    class Factory : AbstractPackageManagerFactory<Sbt>("SBT") {
        override val globsForDefinitionFiles = listOf("build.sbt", "build.scala")

        override fun create(
            analysisRoot: File,
            analyzerConfig: AnalyzerConfiguration,
            repoConfig: RepositoryConfiguration
        ) = Sbt(managerName, analysisRoot, analyzerConfig, repoConfig)
    }

    override fun command(workingDir: File?) = if (Os.isWindows) "sbt.bat" else "sbt"

    override fun getVersion(workingDir: File?): String {
        if (workingDir != null) return super.getVersion(workingDir)

        // Avoid newer Sbt versions to warn about "Neither build.sbt nor a 'project' directory in the current directory"
        // and prompt the user to continue or quit on Windows where the "-batch" option is not supported.
        val dummyProjectDir = createOrtTempDir(managerName).apply {
            resolve("project").mkdir()
        }

        return super.getVersion(dummyProjectDir).also { dummyProjectDir.safeDeleteRecursively(force = true) }
    }

    override fun getVersionArguments() = "${SBT_OPTIONS.joinToString(" ")} sbtVersion"

    override fun transformVersion(output: String): String {
        val versions = output.lines().mapNotNull { line ->
            VERSION_REGEX.matchEntire(line)?.groupValues?.getOrNull(1)?.let { Semver(it) }
        }

        if (versions.isEmpty()) {
            log.warn { "No version match found in output:\n$output" }
        }

        return checkForSameSbtVersion(versions)
    }

    override fun getVersionRequirement(): Requirement =
        // We need at least sbt version 0.13.0 to be able to use "makePom" instead of the deprecated hyphenated
        // form "make-pom" and to support declaring Maven-style repositories, see
        // http://www.scala-sbt.org/0.13/docs/Publishing.html#Modifying+the+generated+POM.
        Requirement.buildIvy("[0.13.0,)")

    private fun checkForSameSbtVersion(versions: List<Semver>): String {
        val uniqueVersions = versions.toSortedSet()
        if (uniqueVersions.size > 1) {
            log.warn { "Different sbt versions used in the same project: $uniqueVersions" }
        }

        return uniqueVersions.firstOrNull()?.toString().orEmpty()
    }

    override fun mapDefinitionFiles(definitionFiles: List<File>): List<File> {
        // Some SBT projects do not have a build file in their root, but they still require "sbt" to be run from the
        // project's root directory. In order to determine the root directory, use the common prefix of all
        // definition file paths.
        val workingDir = getCommonFileParent(definitionFiles) ?: analysisRoot

        log.info { "Determined '$workingDir' as the $managerName project root directory." }

        fun runSbt(vararg command: String) =
            suppressInput {
                run(workingDir, *SBT_OPTIONS, *command)
            }

        // Get the list of project names.
        val internalProjectNames = runSbt("projects").stdout.lines().mapNotNull {
            PROJECT_REGEX.matchEntire(it)?.groupValues?.getOrNull(1)
        }

        if (internalProjectNames.isEmpty()) {
            log.warn { "No SBT project found inside the '$workingDir' directory." }
        }

        // Generate the POM files. Note that a single run of makePom might create multiple POM files in case of
        // aggregate projects.
        val makePomCommand = internalProjectNames.joinToString("") { ";$it/makePom" }
        val pomFiles = runSbt(makePomCommand).stdout.lines().mapNotNull { line ->
            POM_REGEX.matchEntire(line)?.groupValues?.getOrNull(1)?.let { File(it) }
        }

        if (pomFiles.isEmpty()) {
            log.warn { "No generated POM files found inside the '$workingDir' directory." }
        }

        return pomFiles.distinct().map { moveGeneratedPom(it) }
    }

    override fun beforeResolution(definitionFiles: List<File>) {
        // Some SBT projects do not have a build file in their root, but they still require "sbt" to be run from the
        // project's root directory. In order to determine the root directory, use the common prefix of all
        // definition file paths.
        val workingDir = getCommonFileParent(definitionFiles) ?: analysisRoot

        log.info { "Determined '$workingDir' as the $managerName project root directory." }

        // Determine the SBT version(s) being used.
        val rootPropertiesFile = workingDir.resolve("project").resolve("build.properties")
        val propertiesFiles = workingDir.walkBottomUp().filterTo(mutableListOf()) {
            it.isFile && it.name == "build.properties"
        }

        if (rootPropertiesFile !in propertiesFiles) {
            // Note that "sbt sbtVersion" behaves differently when executed inside or outside an SBT project, see
            // https://stackoverflow.com/a/20337575/1127485.
            checkVersion(workingDir)
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
                throw IOException(
                    "Unsupported $managerName version $lowestSbtVersion does not fulfill " +
                            "$sbtVersionRequirement."
                )
            }
        }
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

private fun moveGeneratedPom(pomFile: File): File {
    val targetDirParent = pomFile.absoluteFile.parentFile.searchUpwardsForSubdirectory("target") ?: return pomFile
    val targetFilename = pomFile.relativeTo(targetDirParent).invariantSeparatorsPath.replace('/', '-')
    val targetFile = targetDirParent.resolve(targetFilename)

    if (runCatching { pomFile.toPath().moveTo(targetFile.toPath(), StandardCopyOption.ATOMIC_MOVE) }.isFailure) {
        Sbt.log.error { "Moving '${pomFile.absolutePath}' to '${targetFile.absolutePath}' failed." }
        return pomFile
    }

    return targetFile
}
