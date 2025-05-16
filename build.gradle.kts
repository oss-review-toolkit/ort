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

import git.semver.plugin.gradle.PrintTask

import org.eclipse.jgit.ignore.FastIgnoreRule

import org.jetbrains.gradle.ext.Gradle
import org.jetbrains.gradle.ext.runConfigurations
import org.jetbrains.gradle.ext.settings

plugins {
    // Apply third-party plugins.
    alias(libs.plugins.gitSemver)
    alias(libs.plugins.ideaExt)
}

semver {
    // Do not create an empty release commit when running the "releaseVersion" task.
    createReleaseCommit = false

    // Do not let untracked files bump the version or add a "-SNAPSHOT" suffix.
    noDirtyCheck = true
}

// Only override a default version (which usually is "unspecified"), but not a custom version.
if (version == Project.DEFAULT_VERSION) {
    version = semver.semVersion.takeIf { it.isPreRelease }
        // To get rid of a build part's "+" prefix because Docker tags do not support it, use only the original "build"
        // part as the "pre-release" part.
        ?.toString()?.replace("${semver.defaultPreRelease}+", "")
        // Fall back to a plain version without pre-release or build parts.
        ?: semver.version
}

logger.lifecycle("Building ORT version $version.")

idea {
    project {
        settings {
            runConfigurations {
                // Disable "condensed" multi-line diffs when running tests from the IDE via Gradle run configurations to
                // more easily accept actual results as expected results.
                defaults(Gradle::class.java) {
                    jvmArgs = "-Dkotest.assertions.multi-line-diff=simple"
                }
            }
        }
    }
}

extensions.findByName("develocity")?.withGroovyBuilder {
    getProperty("buildScan")?.withGroovyBuilder {
        setProperty("termsOfUseUrl", "https://gradle.com/terms-of-service")
        setProperty("termsOfUseAgree", "yes")
    }
}

// Gradle's "dependencies" task selector only executes on a single / the current project [1]. However, sometimes viewing
// all dependencies at once is beneficial, e.g. for debugging version conflict resolution.
// [1]: https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html#sec:listing_dependencies
tasks.register("allDependencies") {
    group = "Help"
    description = "Displays all dependencies declared in all projects."

    val dependenciesTasks = getTasksByName("dependencies", /* recursive = */ true)
    dependsOn(dependenciesTasks)

    // Ensure deterministic output by requiring to run tasks after each other in always the same order.
    dependenciesTasks.sorted().zipWithNext().forEach { (a, b) ->
        b.mustRunAfter(a)
    }
}

open class OrtPrintTask : PrintTask({ "" }, "Prints the current project version") {
    private val projectVersion = project.version.toString()

    @TaskAction
    fun printVersion() = println(projectVersion)
}

tasks.replace("printVersion", OrtPrintTask::class.java)

val checkCopyrightsInNoticeFile by tasks.registering {
    val gitFilesProvider = providers.of(GitFilesValueSource::class) { parameters { workingDir = rootDir } }
    val files = CopyrightableFiles.filter(gitFilesProvider)
    val noticeFile = rootDir.resolve("NOTICE")
    val genericHolderPrefix = "The ORT Project Authors"

    inputs.files(files)

    doLast {
        val allCopyrights = mutableSetOf<String>()
        var hasViolations = false

        files.forEach { file ->
            val copyrights = CopyrightUtils.extract(file)
            if (copyrights.isNotEmpty()) {
                allCopyrights += copyrights
            } else {
                hasViolations = true
                logger.error("The file '$file' has no Copyright statement.")
            }
        }

        val notices = noticeFile.readLines()
        CopyrightUtils.extractHolders(allCopyrights).forEach { holder ->
            if (!holder.startsWith(genericHolderPrefix) && notices.none { holder in it }) {
                hasViolations = true
                logger.error("The '$holder' Copyright holder is not captured in '$noticeFile'.")
            }
        }

        if (hasViolations) throw GradleException("There were errors in Copyright statements.")
    }
}

val checkLicenseHeaders by tasks.registering {
    val gitFilesProvider = providers.of(GitFilesValueSource::class) { parameters { workingDir = rootDir } }
    val files = CopyrightableFiles.filter(gitFilesProvider)

    inputs.files(files)

    // To be on the safe side in case any called helper functions are not thread safe.
    mustRunAfter(checkCopyrightsInNoticeFile)

    doLast {
        var hasErrors = false

        files.forEach { file ->
            val headerLines = LicenseUtils.extractHeader(file)

            val holders = CopyrightUtils.extractHolders(headerLines)
            if (holders.singleOrNull() != CopyrightUtils.EXPECTED_HOLDER) {
                hasErrors = true
                logger.error("Unexpected copyright holder(s) in file '$file': $holders")
            }

            if (!headerLines.joinToString("\n").endsWith(LicenseUtils.expectedHeader)) {
                hasErrors = true
                logger.error("Unexpected license header in file '$file'.")
            }
        }

        if (hasErrors) throw GradleException("There were errors in license headers.")
    }
}

val checkGitAttributes by tasks.registering {
    val gitFilesProvider = providers.of(GitFilesValueSource::class) { parameters { workingDir = rootDir } }

    inputs.files(gitFilesProvider)

    doLast {
        var hasErrors = false

        val files = gitFilesProvider.get()
        val gitAttributesFiles = files.filter { it.endsWith(".gitattributes") }
        val commentChars = setOf('#', '/')

        gitAttributesFiles.forEach { gitAttributesFile ->
            logger.lifecycle("Checking file '$gitAttributesFile'...")

            val ignoreRules = gitAttributesFile.readLines()
                // Skip empty and comment lines.
                .map { it.trim() }
                .filter { it.isNotEmpty() && it.first() !in commentChars }
                // The pattern is the part before the first whitespace.
                .mapTo(mutableSetOf()) { line -> line.takeWhile { !it.isWhitespace() } }
                // Create ignore rules from valid patterns.
                .mapIndexedNotNull { index, pattern ->
                    runCatching {
                        FastIgnoreRule(pattern)
                    }.onFailure {
                        logger.warn("File '$gitAttributesFile' contains an invalid pattern in line ${index + 1}: $it")
                    }.getOrNull()
                }

            // Check only those files that are in scope of this ".gitattributes" file.
            val gitAttributesDir = gitAttributesFile.parentFile
            val filesInScope = files.filter { it.startsWith(gitAttributesDir) }

            ignoreRules.forEach { rule ->
                val matchesAnything = filesInScope.any { file ->
                    val relativeFile = file.relativeTo(gitAttributesDir)
                    rule.isMatch(relativeFile.invariantSeparatorsPath, /* directory = */ false)
                }

                if (!matchesAnything) {
                    hasErrors = true
                    logger.error("Rule '$rule' does not match anything.")
                }
            }
        }

        if (hasErrors) throw GradleException("There were stale '.gitattribute' entries.")
    }
}

tasks.register<GeneratePluginDocsTask>("generatePluginDocs") {
    val kspKotlinTasks = getTasksByName("kspKotlin", /* recursive = */ true)
    val outputFiles = kspKotlinTasks.flatMap { it.outputs.files }
    inputFiles = files(outputFiles).asFileTree.matching { include("**/META-INF/plugin/*.json") }

    // TODO: This explicit dependency should not be necessary if tasks were following the best practice described at
    //       https://docs.gradle.org/current/samples/sample_cross_project_output_sharing.html. However, this requires
    //       larger refactorings.
    dependsOn(kspKotlinTasks)
}
