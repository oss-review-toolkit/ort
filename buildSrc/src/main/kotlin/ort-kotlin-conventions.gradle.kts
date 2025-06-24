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

val javaLanguageVersion: String by project

plugins {
    // Apply core plugins.
    jacoco

    // Apply precompiled plugins.
    id("ort-base-conventions")

    // Apply third-party plugins.
    id("com.autonomousapps.dependency-analysis")
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

                implementation(libs.kotest.assertions.core)
                implementation(libs.kotest.runner.junit5)
            }
        }

        register<JvmTestSuite>("funTest")
    }
}

// Associate the "funTest" compilation with the "main" compilation to be able to access "internal" objects from
// functional tests.
kotlin.target.compilations.apply {
    getByName("funTest").associateWith(getByName(KotlinCompilation.MAIN_COMPILATION_NAME))
}

dependencies {
    detektPlugins(project(":detekt-rules"))

    detektPlugins(libs.plugin.detekt.formatting)

    implementation(libs.log4j.api.kotlin)

    constraints {
        implementation("org.jetbrains.kotlin:kotlin-reflect:${libs.versions.kotlinPlugin.get()}") {
            because("All transitive versions of Kotlin reflect need to match ORT's version of Kotlin.")
        }

        implementation(libs.jruby) {
            because("JRuby used by Bundler directly and by AsciidoctorJ transitively must match.")
        }
    }
}

detekt {
    // Only configure differences to the default.
    buildUponDefaultConfig = true
    config.from(files("$rootDir/.detekt.yml"))

    source.from(fileTree(".") { include("*.gradle.kts") }, "src/funTest/kotlin", "src/testFixtures/kotlin")

    basePath = rootDir.path
}

java {
    // Register functional tests as a feature of the main library, see
    // https://docs.gradle.org/current/userguide/how_to_create_feature_variants_of_a_library.html.
    registerFeature("funTest") {
        usingSourceSet(sourceSets["funTest"])
    }

    toolchain {
        // Note that Gradle currently matches the Java language version exactly and does not consider (backward)
        // compatibility between versions, see https://github.com/gradle/gradle/issues/16256.
        languageVersion = JavaLanguageVersion.of(javaLanguageVersion)
    }
}

tasks.withType<Jar>().configureEach {
    manifest {
        attributes["Build-Jdk"] = javaToolchains.compilerFor(java.toolchain).map { it.metadata.jvmVersion }
    }
}

val maxKotlinJvmTarget = runCatching { JvmTarget.fromTarget(javaLanguageVersion) }
    .getOrDefault(enumValues<JvmTarget>().max())

val mergeDetektReportsTaskName = "mergeDetektReports"
val mergeDetektReports = if (rootProject.tasks.findByName(mergeDetektReportsTaskName) != null) {
    rootProject.tasks.named<ReportMergeTask>(mergeDetektReportsTaskName)
} else {
    rootProject.tasks.register<ReportMergeTask>(mergeDetektReportsTaskName) {
        output = rootProject.layout.buildDirectory.file("reports/detekt/merged.sarif")
    }
}

val detekt = tasks.named<Detekt>("detekt")

tasks.withType<Detekt>().configureEach detekt@{
    jvmTarget = maxKotlinJvmTarget.target

    dependsOn(":detekt-rules:assemble")
    if (this != detekt.get()) mustRunAfter(detekt)

    exclude {
        "/build/generated/" in it.file.absoluteFile.invariantSeparatorsPath
    }

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

tasks.register("detektAll") {
    group = "Verification"
    description = "Run all detekt tasks with type resolution."

    dependsOn(tasks.withType<Detekt>().filterNot { it.name == "detekt" })
}

tasks.withType<KotlinCompile>().configureEach {
    val hasSerializationPlugin = plugins.hasPlugin(libs.plugins.kotlinSerialization.get().pluginId)

    val optInRequirements = listOfNotNull(
        "kotlin.ExperimentalStdlibApi",
        "kotlin.contracts.ExperimentalContracts",
        "kotlin.io.encoding.ExperimentalEncodingApi",
        "kotlin.io.path.ExperimentalPathApi",
        "kotlin.time.ExperimentalTime",
        "kotlinx.serialization.ExperimentalSerializationApi".takeIf { hasSerializationPlugin }
    )

    compilerOptions {
        allWarningsAsErrors = true
        freeCompilerArgs = listOf(
            "-Xannotation-default-target=param-property",
            "-Xconsistent-data-class-copy-visibility",
            // Work-around for https://youtrack.jetbrains.com/issue/KT-78352.
            "-Xwarning-level=IDENTITY_SENSITIVE_OPERATIONS_WITH_VALUE_TYPE:disabled"
        )
        jvmTarget = maxKotlinJvmTarget
        optIn = optInRequirements
    }
}

tasks.register<Jar>("sourcesJar") {
    archiveClassifier = "sources"
    from(sourceSets.main.get().allSource)
}

tasks.register<Jar>("javadocJar") {
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
            "--add-opens", "java.base/java.lang=ALL-UNNAMED",
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
