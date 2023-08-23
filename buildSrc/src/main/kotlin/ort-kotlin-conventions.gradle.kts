/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.report.ReportMergeTask

import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.JavaVersion
import org.gradle.api.attributes.TestSuiteType
import org.gradle.api.plugins.jvm.JvmTestSuite
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

private val Project.libs: LibrariesForLibs
    get() = extensions.getByType()

plugins {
    // Apply core plugins.
    jacoco
    `maven-publish`
    signing

    // Apply precompiled plugins.
    id("ort-base-conventions")

    // Apply third-party plugins.
    id("dev.adamko.dokkatoo")
    id("io.gitlab.arturbosch.detekt")
    kotlin("jvm")
}

testing {
    suites {
        withType<JvmTestSuite>().configureEach {
            useJUnitJupiter()

            dependencies {
                implementation(project(":utils:test-utils"))

                implementation(libs.kotestAssertionsCore)
                implementation(libs.kotestRunnerJunit5)
            }
        }

        register<JvmTestSuite>("funTest") {
            sources {
                kotlin {
                    testType = TestSuiteType.FUNCTIONAL_TEST
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

configurations.all {
    resolutionStrategy {
        // Ensure all OkHttp versions match our version >= 4 to avoid Kotlin vs. Java issues with OkHttp 3.
        force(libs.okhttp)

        // Ensure all JRuby versions match our version to avoid Psych YAML library issues.
        force(libs.jruby)

        // Ensure all Log4j API versions match our version.
        force(libs.log4jApi)

        // Ensure that all transitive versions of Kotlin libraries match our version of Kotlin.
        force("org.jetbrains.kotlin:kotlin-reflect:${libs.versions.kotlinPlugin.get()}")
    }
}

// Note: Kotlin DSL cannot directly access configurations that are created by applying a plugin in the very same
// project, thus put configuration names in quotes to leverage lazy lookup.
dependencies {
    "detektPlugins"(project(":detekt-rules"))

    "detektPlugins"("io.gitlab.arturbosch.detekt:detekt-formatting:${libs.versions.detektPlugin.get()}")
}

detekt {
    // Only configure differences to the default.
    buildUponDefaultConfig = true
    config.from(files("$rootDir/.detekt.yml"))

    source.from(fileTree(".") { include("*.gradle.kts") }, "src/funTest/kotlin")

    basePath = rootDir.path
}

val javaVersion = JavaVersion.current()
val maxKotlinJvmTarget = runCatching { JvmTarget.fromTarget(javaVersion.majorVersion) }
    .getOrDefault(JvmTarget.entries.max())

val mergeDetektReportsTaskName = "mergeDetektReports"
val mergeDetektReports = if (rootProject.tasks.findByName(mergeDetektReportsTaskName) != null) {
    rootProject.tasks.named<ReportMergeTask>(mergeDetektReportsTaskName)
} else {
    rootProject.tasks.register<ReportMergeTask>(mergeDetektReportsTaskName) {
        output = rootProject.layout.buildDirectory.dir("reports/detekt/merged.sarif").get().asFile
    }
}

tasks.withType<Detekt> detekt@{
    jvmTarget = maxKotlinJvmTarget.target

    dependsOn(":detekt-rules:assemble")

    reports {
        html.required = false

        // TODO: Enable this once https://github.com/detekt/detekt/issues/5034 is resolved and use the merged
        //       Markdown file as a GitHub Action job summary, see
        //       https://github.blog/2022-05-09-supercharging-github-actions-with-job-summaries/.
        md.required = false

        sarif.required = true
        txt.required = false
        xml.required = false
    }

    mergeDetektReports.configure {
        input.from(this@detekt.sarifReportFile)
    }

    finalizedBy(mergeDetektReports)
}

tasks.withType<JavaCompile>().configureEach {
    // Align this with Kotlin to avoid errors, see https://youtrack.jetbrains.com/issue/KT-48745.
    sourceCompatibility = maxKotlinJvmTarget.target
    targetCompatibility = maxKotlinJvmTarget.target
}

tasks.withType<KotlinCompile>().configureEach {
    val customCompilerArgs = listOf(
        "-opt-in=kotlin.contracts.ExperimentalContracts",
        "-opt-in=kotlin.io.path.ExperimentalPathApi",
        "-opt-in=kotlin.time.ExperimentalTime"
    )

    compilerOptions {
        allWarningsAsErrors = true
        freeCompilerArgs.addAll(customCompilerArgs)
        jvmTarget = maxKotlinJvmTarget
    }
}

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier = "sources"
    from(sourceSets.main.get().allSource)
}

tasks.register<Jar>("docsHtmlJar") {
    description = "Assembles a JAR containing the HTML documentation."
    group = "Documentation"

    dependsOn(tasks.dokkatooGeneratePublicationHtml)
    from(tasks.dokkatooGeneratePublicationHtml.flatMap { it.outputDirectory })
    archiveClassifier = "htmldoc"
}

val docsJavadocJar by tasks.registering(Jar::class) {
    description = "Assembles a JAR containing the Javadoc documentation."
    group = "Documentation"

    dependsOn(tasks.dokkatooGeneratePublicationJavadoc)
    from(tasks.dokkatooGeneratePublicationJavadoc.flatMap { it.outputDirectory })
    archiveClassifier = "javadoc"
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
        jvmArgs(
            "--add-opens", "java.base/java.io=ALL-UNNAMED",
            "--add-opens", "java.base/java.util=ALL-UNNAMED",
            "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED"
        )
    }

    val testSystemProperties = mutableListOf("gradle.build.dir" to project.layout.buildDirectory.get().asFile.path)

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
        showCauses = false
        showStackTraces = false
        showStandardStreams = false
    }
}

tasks.named("check") {
    dependsOn(tasks["funTest"])
}

tasks.named<JacocoReport>("jacocoTestReport") {
    reports {
        // Enable XML in addition to HTML for CI integration.
        xml.required = true
    }
}

tasks.register<JacocoReport>("jacocoFunTestReport") {
    description = "Generates code coverage report for the funTest task."
    group = "Reporting"

    executionData(tasks["funTest"])
    sourceSets(sourceSets.main.get())

    reports {
        // Enable XML in addition to HTML for CI integration.
        xml.required = true
    }
}

tasks.register("jacocoReport") {
    description = "Generates code coverage reports for all test tasks."
    group = "Reporting"

    dependsOn(tasks.withType<JacocoReport>())
}

configure<PublishingExtension> {
    publications {
        val publicationName = name.replace(Regex("([a-z])-([a-z])")) {
            "${it.groupValues[1]}${it.groupValues[2].uppercase()}"
        }

        create<MavenPublication>(publicationName) {
            fun getGroupId(parent: Project?): String =
                if (parent == null) "" else "${getGroupId(parent.parent)}.${parent.name.replace("-", "")}"

            groupId = "org${getGroupId(parent)}"

            from(components["java"])
            artifact(sourcesJar)
            artifact(docsJavadocJar)

            pom {
                licenses {
                    license {
                        name = "Apache-2.0"
                        url = "https://www.apache.org/licenses/LICENSE-2.0"
                    }
                }

                scm {
                    connection = "scm:git:https://github.com/oss-review-toolkit/ort.git"
                    developerConnection = "scm:git:git@github.com:oss-review-toolkit/ort.git"
                    tag = version.toString()
                    url = "https://github.com/oss-review-toolkit/ort"
                }
            }
        }
    }

    repositories {
        maven {
            name = "OSSRH"

            val releasesRepoUrl = "https://oss.sonatype.org/service/local/staging/deploy/maven2"
            val snapshotsRepoUrl = "https://oss.sonatype.org/content/repositories/snapshots"
            url = uri(if (version.toString().endsWith("-SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl)

            credentials {
                username = System.getenv("OSSRH_USER") ?: return@credentials
                password = System.getenv("OSSRH_PASSWORD") ?: return@credentials
            }
        }
    }
}

signing {
    val signingKey = System.getenv("SIGNING_KEY") ?: return@signing
    val signingPassword = System.getenv("SIGNING_PASSWORD") ?: return@signing
    useInMemoryPgpKeys(signingKey, signingPassword)

    setRequired {
        // Do not require signing for `PublishToMavenLocal` tasks only.
        gradle.taskGraph.allTasks.any { it is PublishToMavenRepository }
    }

    sign(publishing.publications)
}
