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

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.report.ReportMergeTask

import java.net.URL

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.revwalk.RevWalk
import org.eclipse.jgit.treewalk.TreeWalk

import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

import org.jetbrains.gradle.ext.Gradle
import org.jetbrains.gradle.ext.runConfigurations
import org.jetbrains.gradle.ext.settings
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

@Suppress("DSL_SCOPE_VIOLATION") // See https://youtrack.jetbrains.com/issue/KTIJ-19369.
plugins {
    alias(libs.plugins.dependencyAnalysis)
    alias(libs.plugins.detekt)
    alias(libs.plugins.dokka)
    alias(libs.plugins.ideaExt)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.versionCatalogUpdate)
    alias(libs.plugins.versions)
}

buildscript {
    dependencies {
        classpath(libs.jgit)
    }

    configurations.all {
        resolutionStrategy {
            // Work around the Kotlin plugin to depend on an outdated version of the Download plugin, see
            // https://youtrack.jetbrains.com/issue/KT-53822.
            force("de.undercouch:gradle-download-task:${libs.versions.downloadPlugin.get()}")
        }
    }
}

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

// Note that Gradle's Java toolchain mechanism cannot be used here as that only applies to the Java version used in
// compile tasks. But already ORT's build scripts, like the compilation of this file itself, depend on Java 11 due to
// the Java target used by some plugins, see e.g. https://github.com/martoe/gradle-svntools-plugin#version-compatibility.
val javaVersion = JavaVersion.current()
if (!javaVersion.isCompatibleWith(JavaVersion.VERSION_11)) {
    throw GradleException("At least Java 11 is required, but Java $javaVersion is being used.")
}

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

tasks.named<DependencyUpdatesTask>("dependencyUpdates").configure {
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

versionCatalogUpdate {
    // Keep the custom sorting / grouping.
    sortByKey.set(false)
}

val mergeDetektReports by tasks.registering(ReportMergeTask::class) {
    output.set(rootProject.buildDir.resolve("reports/detekt/merged.sarif"))
}

allprojects {
    repositories {
        mavenCentral()
    }

    apply(plugin = "io.gitlab.arturbosch.detekt")

    // Note: Kotlin DSL cannot directly access configurations that are created by applying a plugin in the very same
    // project, thus put configuration names in quotes to leverage lazy lookup.
    dependencies {
        "detektPlugins"(project(":detekt-rules"))

        "detektPlugins"("io.gitlab.arturbosch.detekt:detekt-formatting:${rootProject.libs.versions.detektPlugin.get()}")
    }

    detekt {
        // Only configure differences to the default.
        buildUponDefaultConfig = true
        config = files("$rootDir/.detekt.yml")

        source.from(fileTree(".") { include("*.gradle.kts") }, "src/funTest/kotlin")

        basePath = rootProject.projectDir.path
    }

    tasks.withType<Detekt> detekt@{
        dependsOn(":detekt-rules:assemble")

        reports {
            html.required.set(false)

            // TODO: Enable this once https://github.com/detekt/detekt/issues/5034 is resolved and use the merged
            //       Markdown file as a GitHub Action job summary, see
            //       https://github.blog/2022-05-09-supercharging-github-actions-with-job-summaries/.
            md.required.set(false)

            sarif.required.set(true)
            txt.required.set(false)
            xml.required.set(false)
        }

        finalizedBy(mergeDetektReports)

        mergeDetektReports.configure {
            input.from(this@detekt.sarifReportFile)
        }
    }
}

subprojects {
    version = rootProject.version

    if (name in listOf("reporter-web-app", "package-curation-providers", "package-managers")) return@subprojects

    // Apply core plugins.
    apply(plugin = "jacoco")
    apply(plugin = "maven-publish")

    // Apply third-party plugins.
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.dokka")

    testing {
        suites {
            withType<JvmTestSuite>().configureEach {
                useJUnitJupiter()

                dependencies {
                    implementation(project(":utils:test-utils"))

                    implementation(rootProject.libs.kotestAssertionsCore)
                    implementation(rootProject.libs.kotestRunnerJunit5)
                }
            }

            register<JvmTestSuite>("funTest") {
                sources {
                    kotlin {
                        testType.set(TestSuiteType.FUNCTIONAL_TEST)
                    }
                }
            }
        }
    }

    // Associate the "funTest" compilation with the "main" compilation to be able to access "internal" objects from
    // functional tests.
    kotlin.target.compilations.apply {
        getByName("funTest").associateWith(getByName(KotlinCompilation.MAIN_COMPILATION_NAME))
    }

    dependencies {
        implementation("org.jetbrains.kotlin:kotlin-stdlib:${rootProject.libs.versions.kotlinPlugin.get()}")
    }

    configurations.all {
        // Do not tamper with configurations related to the detekt plugin, for some background information
        // https://github.com/detekt/detekt/issues/2501.
        if (!name.startsWith("detekt")) {
            resolutionStrategy {
                // Ensure all OkHttp versions match our version >= 4 to avoid Kotlin vs. Java issues with OkHttp 3.
                force(rootProject.libs.okhttp)

                // Ensure all JRuby versions match our version to avoid Psych YAML library issues.
                force(rootProject.libs.jruby)

                // Ensure all Log4j API versions match our version.
                force(rootProject.libs.log4jApi)

                // Ensure that all transitive versions of Kotlin libraries match our version of Kotlin.
                force("org.jetbrains.kotlin:kotlin-reflect:${rootProject.libs.versions.kotlinPlugin.get()}")
            }
        }
    }

    tasks.withType<KotlinCompile>().configureEach {
        val customCompilerArgs = listOf(
            "-opt-in=kotlin.contracts.ExperimentalContracts",
            "-opt-in=kotlin.io.path.ExperimentalPathApi",
            "-opt-in=kotlin.time.ExperimentalTime"
        )

        kotlinOptions {
            allWarningsAsErrors = true
            apiVersion = "1.8"
            freeCompilerArgs = freeCompilerArgs + customCompilerArgs
            jvmTarget = javaVersion.majorVersion
        }
    }

    tasks.dokkaHtml.configure {
        dokkaSourceSets {
            configureEach {
                jdkVersion.set(17)

                val jacksonVersion = libs.versions.jackson.get()
                val log4jApiVersion = libs.versions.log4jApi.get()

                externalDocumentationLink {
                    val baseUrl = "https://codehaus-plexus.github.io/plexus-containers/plexus-container-default/apidocs"
                    url.set(URL(baseUrl))
                    packageListUrl.set(URL("$baseUrl/package-list"))
                }

                externalDocumentationLink {
                    val majorMinorVersion = jacksonVersion.split('.').let { "${it[0]}.${it[1]}" }
                    val baseUrl = "https://fasterxml.github.io/jackson-databind/javadoc/$majorMinorVersion"
                    url.set(URL(baseUrl))
                    packageListUrl.set(URL("$baseUrl/package-list"))
                }

                externalDocumentationLink {
                    val baseUrl = "https://jakewharton.github.io/DiskLruCache"
                    url.set(URL(baseUrl))
                    packageListUrl.set(URL("$baseUrl/package-list"))
                }

                externalDocumentationLink {
                    val majorVersion = log4jApiVersion.substringBefore('.')
                    val baseUrl = "https://logging.apache.org/log4j/$majorVersion.x/log4j-api/apidocs"
                    url.set(URL(baseUrl))
                    packageListUrl.set(URL("$baseUrl/package-list"))
                }
            }
        }
    }

    tasks.withType<Test>().configureEach {
        // Work-around for "--tests" only being able to include tests, see https://github.com/gradle/gradle/issues/6505.
        properties["tests.exclude"]?.also { excludes ->
            filter {
                excludes.toString().split(',').map { excludeTestsMatching(it) }
                isFailOnNoMatchingTests = false
            }
        }

        // Convenience alternative to "--tests" that can take multiple patterns at once as Gradle is not planning to
        // implement this, see https://github.com/gradle/gradle/issues/5719.
        properties["tests.include"]?.also { includes ->
            filter {
                includes.toString().split(',').map { includeTestsMatching(it) }
                isFailOnNoMatchingTests = false
            }
        }

        if (javaVersion.isCompatibleWith(JavaVersion.VERSION_17)) {
            // See https://kotest.io/docs/next/extensions/system_extensions.html#system-environment.
            jvmArgs("--add-opens=java.base/java.util=ALL-UNNAMED")
        }

        val testSystemProperties = mutableListOf("gradle.build.dir" to project.buildDir.path)

        listOf(
            "java.io.tmpdir",
            "kotest.assertions.multi-line-diff",
            "kotest.tags"
        ).mapNotNullTo(testSystemProperties) { key ->
            System.getProperty(key)?.let { key to it }
        }

        systemProperties = testSystemProperties.toMap()

        testLogging {
            events = setOf(TestLogEvent.STARTED, TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
            exceptionFormat = TestExceptionFormat.FULL
            showStandardStreams = false
        }
    }

    tasks.named<JacocoReport>("jacocoTestReport").configure {
        reports {
            // Enable XML in addition to HTML for CI integration.
            xml.required.set(true)
        }
    }

    tasks.register<JacocoReport>("jacocoFunTestReport").configure {
        description = "Generates code coverage report for the funTest task."
        group = "Reporting"

        executionData(tasks["funTest"])
        sourceSets(sourceSets["main"])

        reports {
            // Enable XML in addition to HTML for CI integration.
            xml.required.set(true)
        }
    }

    tasks.register("jacocoReport").configure {
        description = "Generates code coverage reports for all test tasks."
        group = "Reporting"

        dependsOn(tasks.withType<JacocoReport>())
    }

    tasks.named("check").configure {
        dependsOn(tasks["funTest"])
    }

    tasks.withType<Jar>().configureEach {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true

        manifest {
            attributes["Implementation-Version"] = project.version
        }
    }

    tasks.register<Jar>("sourcesJar").configure {
        archiveClassifier.set("sources")
        from(sourceSets["main"].allSource)
    }

    tasks.register<Jar>("dokkaHtmlJar").configure {
        dependsOn(tasks.dokkaHtml)

        description = "Assembles a jar archive containing the minimalistic HTML documentation."
        group = "Documentation"

        archiveClassifier.set("dokka")
        from(tasks.dokkaHtml)
    }

    tasks.register<Jar>("dokkaJavadocJar").configure {
        dependsOn(tasks.dokkaJavadoc)

        description = "Assembles a jar archive containing the Javadoc documentation."
        group = "Documentation"

        archiveClassifier.set("javadoc")
        from(tasks.dokkaJavadoc)
    }

    configure<PublishingExtension> {
        publications {
            create<MavenPublication>(name) {
                fun getGroupId(parent: Project?): String =
                    if (parent == null) "" else "${getGroupId(parent.parent)}.${parent.name.replace("-", "")}"

                groupId = "org${getGroupId(parent)}"

                from(components["java"])
                artifact(tasks["sourcesJar"])
                artifact(tasks["dokkaJavadocJar"])

                pom {
                    licenses {
                        license {
                            name.set("Apache-2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0")
                        }
                    }

                    scm {
                        connection.set("scm:git:https://github.com/oss-review-toolkit/ort.git")
                        developerConnection.set("scm:git:git@github.com:oss-review-toolkit/ort.git")
                        tag.set(version.toString())
                        url.set("https://github.com/oss-review-toolkit/ort")
                    }
                }
            }
        }
    }
}

// Gradle's "dependencies" task selector only executes on a single / the current project [1]. However, sometimes viewing
// all dependencies at once is beneficial, e.g. for debugging version conflict resolution.
// [1]: https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html#sec:listing_dependencies
tasks.register("allDependencies").configure {
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
    "reporter/src/main/resources/META_INF/",
    "reporter/src/main/resources/pdf-theme/pdf-theme.yml",
    "reporter/src/main/resources/prismjs/",
    "reporter/src/main/resources/templates/freemarker_implicit.ftl",
    "reporter-web-app/yarn.lock",
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
        var hasViolations = false

        files.forEach { file ->
            val headerLines = extractLicenseHeader(file)

            val holders = extractCopyrightHolders(headerLines)
            if (holders.singleOrNull() != expectedCopyrightHolder) {
                logger.error("Unexpected copyright holder(s) in file '$file': $holders")
            }

            if (!headerLines.joinToString("\n").endsWith(expectedLicenseHeader)) {
                hasViolations = true
                logger.error("Unexpected license header in file '$file'.")
            }
        }

        if (hasViolations) throw GradleException("There were errors in license headers.")
    }
}
