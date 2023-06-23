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

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.ignore.FastIgnoreRule
import org.eclipse.jgit.lib.Config
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.storage.file.FileBasedConfig
import org.eclipse.jgit.treewalk.TreeWalk
import org.eclipse.jgit.util.FS
import org.eclipse.jgit.util.SystemReader

import org.jetbrains.gradle.ext.Gradle
import org.jetbrains.gradle.ext.runConfigurations
import org.jetbrains.gradle.ext.settings

plugins {
    // Apply third-party plugins.
    alias(libs.plugins.dependencyAnalysis)
    alias(libs.plugins.ideaExt)
    alias(libs.plugins.versions)
}

buildscript {
    repositories {
        mavenCentral()
    }

    dependencies {
        classpath(libs.jgit)
    }
}

class GitConfigNoSystemReader(private val delegate: SystemReader) : SystemReader() {
    override fun getenv(variable: String): String? {
        if (variable == "GIT_CONFIG_NOSYSTEM") return "true"
        return delegate.getenv(variable)
    }

    override fun openSystemConfig(parent: Config?, fs: FS): FileBasedConfig =
        object : FileBasedConfig(parent, null, fs) {
            override fun load() = Unit
            override fun isOutdated(): Boolean = false
        }

    override fun getHostname(): String = delegate.hostname
    override fun getProperty(key: String): String? = delegate.getProperty(key)
    override fun openUserConfig(parent: Config?, fs: FS): FileBasedConfig = delegate.openUserConfig(parent, fs)
    override fun openJGitConfig(parent: Config?, fs: FS): FileBasedConfig = delegate.openJGitConfig(parent, fs)
    override fun getCurrentTime(): Long = delegate.currentTime
    override fun getTimezone(`when`: Long): Int = delegate.getTimezone(`when`)
}

SystemReader.setInstance(GitConfigNoSystemReader(SystemReader.getInstance()))

// Only override a default version (which usually is "unspecified"), but not a custom version.
if (version == Project.DEFAULT_VERSION) {
    version = Git.open(rootDir).use { git ->
        // Make the output exactly match "git describe --abbrev=10 --always --tags --dirty --match=[0-9]*", which is
        // what is used in "scripts/docker_build.sh", to make the hash match what JitPack uses.
        val description = git.describe().setAbbrev(10).setAlways(true).setTags(true).setMatch("[0-9]*").call()

        // Simulate the "--dirty" option with JGit.
        description.takeUnless { git.status().call().hasUncommittedChanges() } ?: "$description-dirty"
    }
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

extensions.findByName("buildScan")?.withGroovyBuilder {
    setProperty("termsOfServiceUrl", "https://gradle.com/terms-of-service")
    setProperty("termsOfServiceAgree", "yes")
}

tasks.named<DependencyUpdatesTask>("dependencyUpdates") {
    gradleReleaseChannel = "current"
    outputFormatter = "json"

    val nonFinalQualifiers = listOf(
        "alpha", "b", "beta", "cr", "dev", "ea", "eap", "m", "milestone", "pr", "preview", "rc", "\\d{14}"
    ).joinToString("|", "(", ")")

    val nonFinalQualifiersRegex = Regex(".*[.-]$nonFinalQualifiers[.\\d-+]*", RegexOption.IGNORE_CASE)

    rejectVersionIf {
        candidate.version.matches(nonFinalQualifiersRegex)
    }
}

// Gradle's "dependencies" task selector only executes on a single / the current project [1]. However, sometimes viewing
// all dependencies at once is beneficial, e.g. for debugging version conflict resolution.
// [1]: https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html#sec:listing_dependencies
tasks.register("allDependencies") {
    val dependenciesTasks = allprojects.map { it.tasks.named<DependencyReportTask>("dependencies") }
    dependsOn(dependenciesTasks)

    // Ensure deterministic output by requiring to run tasks after each other in always the same order.
    dependenciesTasks.zipWithNext().forEach { (a, b) ->
        b.configure {
            mustRunAfter(a)
        }
    }
}

fun getCommittedFilePaths(rootDir: File): List<String> {
    val filePaths = mutableListOf<String>()

    Git.open(rootDir).use { git ->
        TreeWalk(git.repository).use { treeWalk ->
            val headCommit = RevWalk(git.repository).use {
                val head = git.repository.resolve(Constants.HEAD)
                it.parseCommit(head)
            }

            with(treeWalk) {
                addTree(headCommit.tree)
                isRecursive = true
            }

            while (treeWalk.next()) {
                filePaths += treeWalk.pathString
            }
        }
    }

    return filePaths
}

val copyrightExcludedPaths = listOf(
    "LICENSE",
    "NOTICE",
    "batect",
    "gradlew",
    "gradle/",
    "examples/",
    "integrations/completions/",
    "plugins/reporters/asciidoc/src/main/resources/pdf-theme/pdf-theme.yml",
    "plugins/reporters/asciidoc/src/main/resources/templates/freemarker_implicit.ftl",
    "plugins/reporters/fossid/src/main/resources/templates/freemarker_implicit.ftl",
    "plugins/reporters/freemarker/src/main/resources/templates/freemarker_implicit.ftl",
    "plugins/reporters/static-html/src/main/resources/prismjs/",
    "plugins/reporters/web-app-template/yarn.lock",
    "resources/META-INF/",
    "resources/exceptions/",
    "resources/licenses/",
    "resources/licenserefs/",
    "test/assets/",
    "funTest/assets/"
)

val copyrightExcludedExtensions = listOf(
    "css",
    "graphql",
    "json",
    "md",
    "png",
    "svg",
    "ttf"
)

fun getCopyrightableFiles(rootDir: File): List<File> =
    getCommittedFilePaths(rootDir).map { filePath ->
        rootDir.resolve(filePath)
    }.filter { file ->
        val isHidden = file.toPath().any { it.toString().startsWith(".") }

        !isHidden
                && copyrightExcludedPaths.none { it in file.invariantSeparatorsPath }
                && file.extension !in copyrightExcludedExtensions
    }

val maxCopyrightLines = 50

fun extractCopyrights(file: File): List<String> {
    val copyrights = mutableListOf<String>()

    var lineCounter = 0

    file.useLines { lines ->
        lines.forEach { line ->
            if (++lineCounter > maxCopyrightLines) return@forEach
            val copyright = line.replaceBefore(" Copyright ", "", "").trim()
            if (copyright.isNotEmpty() && !copyright.endsWith("\"")) copyrights += copyright
        }
    }

    return copyrights
}

val copyrightPrefixRegex = Regex("Copyright .*\\d{2,}(-\\d{2,})? ", RegexOption.IGNORE_CASE)

fun extractCopyrightHolders(statements: Collection<String>): List<String> {
    val holders = mutableListOf<String>()

    statements.mapNotNullTo(holders) { statement ->
        val holder = statement.replace(copyrightPrefixRegex, "")
        holder.takeUnless { it == statement }?.trim()
    }

    return holders
}

val checkCopyrightsInNoticeFile by tasks.registering {
    val files = getCopyrightableFiles(rootDir)
    val noticeFile = rootDir.resolve("NOTICE")
    val genericHolderPrefix = "The ORT Project Authors"

    inputs.files(files)

    doLast {
        val allCopyrights = mutableSetOf<String>()
        var hasViolations = false

        files.forEach { file ->
            val copyrights = extractCopyrights(file)
            if (copyrights.isNotEmpty()) {
                allCopyrights += copyrights
            } else {
                hasViolations = true
                logger.error("The file '$file' has no Copyright statement.")
            }
        }

        val notices = noticeFile.readLines()
        extractCopyrightHolders(allCopyrights).forEach { holder ->
            if (!holder.startsWith(genericHolderPrefix) && notices.none { holder in it }) {
                hasViolations = true
                logger.error("The '$holder' Copyright holder is not captured in '$noticeFile'.")
            }
        }

        if (hasViolations) throw GradleException("There were errors in Copyright statements.")
    }
}

val lastLicenseHeaderLine = "License-Filename: LICENSE"

val expectedCopyrightHolder =
    "The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)"

// The header without `lastLicenseHeaderLine` as that line is used as a marker.
val expectedLicenseHeader = """
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

    SPDX-License-Identifier: Apache-2.0
""".trimIndent()

fun extractLicenseHeader(file: File): List<String> {
    var headerLines = file.useLines { lines ->
        lines.takeWhile { !it.endsWith(lastLicenseHeaderLine) }.toList()
    }

    while (true) {
        val uniqueColumnChars = headerLines.mapNotNullTo(mutableSetOf()) { it.firstOrNull() }

        // If there are very few different chars in a column, assume that column to consist of comment chars /
        // indentation only.
        if (uniqueColumnChars.size < 3) {
            val trimmedHeaderLines = headerLines.mapTo(mutableListOf()) { it.drop(1) }
            headerLines = trimmedHeaderLines
        } else {
            break
        }
    }

    return headerLines
}

val checkLicenseHeaders by tasks.registering {
    val files = getCopyrightableFiles(rootDir)

    inputs.files(files)

    // To be on the safe side in case any called helper functions are not thread safe.
    mustRunAfter(checkCopyrightsInNoticeFile)

    doLast {
        var hasErrors = false

        files.forEach { file ->
            val headerLines = extractLicenseHeader(file)

            val holders = extractCopyrightHolders(headerLines)
            if (holders.singleOrNull() != expectedCopyrightHolder) {
                hasErrors = true
                logger.error("Unexpected copyright holder(s) in file '$file': $holders")
            }

            if (!headerLines.joinToString("\n").endsWith(expectedLicenseHeader)) {
                hasErrors = true
                logger.error("Unexpected license header in file '$file'.")
            }
        }

        if (hasErrors) throw GradleException("There were errors in license headers.")
    }
}

val checkGitAttributes by tasks.registering {
    val files = getCommittedFilePaths(rootDir)

    inputs.files(files)

    doLast {
        var hasErrors = false

        val gitAttributesFiles = files.filter { it.endsWith(".gitattributes") }
        val commentChars = setOf('#', '/')

        gitAttributesFiles.forEach { gitAttributes ->
            logger.lifecycle("Checking file '$gitAttributes'...")

            val gitAttributesFile = file(gitAttributes)
            val ignoreRules = gitAttributesFile.readLines()
                // Skip empty and comment lines.
                .map { it.trim() }
                .filter { it.isNotEmpty() && it.first() !in commentChars }
                // The patterns is the part before the first whitespace.
                .mapTo(mutableSetOf()) { line -> line.takeWhile { !it.isWhitespace() } }
                // Create ignore rules from valid patterns.
                .mapIndexedNotNull { index, pattern ->
                    runCatching {
                        FastIgnoreRule(pattern)
                    }.onFailure {
                        logger.warn("File '$gitAttributes' contains an invalid pattern in line ${index + 1}: $it")
                    }.getOrNull()
                }

            // Check only those files that are in scope of this ".gitattributes" file.
            val gitAttributesDir = gitAttributesFile.parentFile
            val filesInScope = files.filter { rootDir.resolve(it).startsWith(gitAttributesDir) }

            ignoreRules.forEach { rule ->
                val matchesAnything = filesInScope.any { file ->
                    val relativeFile = file(file).relativeTo(gitAttributesDir)
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
