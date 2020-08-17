/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

import io.gitlab.arturbosch.detekt.Detekt

import java.net.URL

import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.gradle.ext.JUnit
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

import org.ossreviewtoolkit.gradle.*

val detektPluginVersion: String by project
val kotlinPluginVersion: String by project

val jacksonVersion: String by project
val kotestVersion: String by project
val log4jCoreVersion: String by project
val okhttpVersion: String by project

plugins {
    kotlin("jvm")

    id("io.gitlab.arturbosch.detekt")
    id("org.jetbrains.dokka")

    id("com.github.ben-manes.versions")
    id("org.ajoberstar.reckon")
    id("org.jetbrains.gradle.plugin.idea-ext")
}

reckon {
    scopeFromProp()
    snapshotFromProp()
}

idea {
    project {
        settings {
            runConfigurations {
                defaults<JUnit> {
                    // Disable "condensed" multi-line diffs when running tests from the IDE to more easily accept actual
                    // results as expected results.
                    vmParameters = "-Dkotest.assertions.multi-line-diff=simple"
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
    val nonFinalQualifiers = listOf(
        "alpha", "b", "beta", "cr", "ea", "eap", "m", "milestone", "pr", "preview", "rc"
    ).joinToString("|", "(", ")")

    val nonFinalQualifiersRegex = Regex(".*[.-]$nonFinalQualifiers[.\\d-+]*", RegexOption.IGNORE_CASE)

    gradleReleaseChannel = "current"

    rejectVersionIf {
        candidate.version.matches(nonFinalQualifiersRegex)
    }
}

allprojects {
    buildscript {
        repositories {
            jcenter()
        }
    }

    repositories {
        jcenter()
    }

    apply(plugin = "io.gitlab.arturbosch.detekt")

    // Note: Kotlin DSL cannot directly access configurations that are created by applying a plugin in the very same
    // project, thus put configuration names in quotes to leverage lazy lookup.
    dependencies {
        "detektPlugins"(project(":detekt-rules"))

        "detektPlugins"("io.gitlab.arturbosch.detekt:detekt-formatting:$detektPluginVersion")
    }

    detekt {
        // Align the detekt core and plugin versions.
        toolVersion = detektPluginVersion

        // Only configure differences to the default.
        buildUponDefaultConfig = true
        config = files("$rootDir/.detekt.yml")

        input = files("$rootDir/buildSrc", "build.gradle.kts", "src/main/kotlin", "src/test/kotlin",
            "src/funTest/kotlin")
    }

    tasks.withType<Detekt> {
        dependsOn(":detekt-rules:assemble")
    }
}

subprojects {
    if (name == "reporter-web-app") return@subprojects

    // Apply core plugins.
    apply(plugin = "jacoco")
    apply(plugin = "maven-publish")

    // Apply third-party plugins.
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.dokka")

    sourceSets.create("funTest") {
        withConvention(KotlinSourceSet::class) {
            kotlin.srcDirs("src/funTest/kotlin")
        }
    }

    // Associate the "funTest" compilation with the "main" compilation to be able to access "internal" objects from
    // functional tests.
    // TODO: The feature to access "internal" objects from functional tests is not actually used yet because the IDE
    //       would still highlight such access as an error. Still keep this code around until that bug in the IDE (see
    //       https://youtrack.jetbrains.com/issue/KT-34102) is fixed as the correct syntax for this code was not easy to
    //       determine.
    kotlin.target.compilations.run {
        getByName("funTest").associateWith(getByName(KotlinCompilation.MAIN_COMPILATION_NAME))
    }

    plugins.withType<JavaLibraryPlugin> {
        dependencies {
            "testImplementation"(project(":test-utils"))

            "testImplementation"("io.kotest:kotest-runner-junit5-jvm:$kotestVersion")
            "testImplementation"("io.kotest:kotest-assertions-core-jvm:$kotestVersion")

            "funTestImplementation"(sourceSets["main"].output)
        }

        configurations["funTestImplementation"].extendsFrom(configurations["testImplementation"])
    }

    configurations.all {
        // Do not tamper with configurations related to the detekt plugin, for some background information
        // https://github.com/detekt/detekt/issues/2501.
        if (!name.startsWith("detekt")) {
            resolutionStrategy {
                // Ensure all OkHttp versions match our version >= 4 to avoid Kotlin vs. Java issues with OkHttp 3.
                force("com.squareup.okhttp3:okhttp:$okhttpVersion")

                // Ensure all API library versions match our core library version.
                force("org.apache.logging.log4j:log4j-api:$log4jCoreVersion")

                // Ensure that all transitive versions of "kotlin-reflect" match our version of Kotlin.
                force("org.jetbrains.kotlin:kotlin-reflect:$kotlinPluginVersion")
            }
        }
    }

    tasks.withType<KotlinCompile>().configureEach {
        val customCompilerArgs = listOf("-Xallow-result-return-type", "-Xopt-in=kotlin.time.ExperimentalTime")

        kotlinOptions {
            allWarningsAsErrors = true
            jvmTarget = "1.8"
            apiVersion = "1.4"
            freeCompilerArgs = freeCompilerArgs + customCompilerArgs
        }
    }

    tasks.named<DokkaTask>("dokka") {
        configuration {
            jdkVersion = 8

            externalDocumentationLink {
                val baseUrl = "https://codehaus-plexus.github.io/plexus-containers/plexus-container-default/apidocs"
                url = URL(baseUrl)
                packageListUrl = URL("$baseUrl/package-list")
            }

            externalDocumentationLink {
                val majorMinorVersion = jacksonVersion.split('.').let { "${it[0]}.${it[1]}" }
                val baseUrl = "https://fasterxml.github.io/jackson-databind/javadoc/$majorMinorVersion"
                url = URL(baseUrl)
                packageListUrl = URL("$baseUrl/package-list")
            }

            externalDocumentationLink {
                val baseUrl = "https://jakewharton.github.io/DiskLruCache"
                url = URL(baseUrl)
                packageListUrl = URL("$baseUrl/package-list")
            }

            externalDocumentationLink {
                val majorVersion = log4jCoreVersion.substringBefore('.')
                val baseUrl = "https://logging.apache.org/log4j/$majorVersion.x/log4j-api/apidocs"
                url = URL(baseUrl)
                packageListUrl = URL("$baseUrl/package-list")
            }
        }
    }

    val funTest by tasks.registering(Test::class) {
        description = "Runs the functional tests."
        group = "Verification"

        classpath = sourceSets["funTest"].runtimeClasspath
        testClassesDirs = sourceSets["funTest"].output.classesDirs
    }

    // Enable JaCoCo only if a JacocoReport task is in the graph as JaCoCo
    // is using "append = true" which disables Gradle's build cache.
    gradle.taskGraph.whenReady {
        val enabled = allTasks.any { it is JacocoReport }

        tasks.withType<Test>().configureEach {
            extensions.configure(JacocoTaskExtension::class) {
                isEnabled = enabled
            }

            systemProperties = listOf(
                "kotest.assertions.multi-line-diff",
                "kotest.tags.include",
                "kotest.tags.exclude"
            ).associateWith { System.getProperty(it) }

            testLogging {
                events = setOf(TestLogEvent.STARTED, TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
                exceptionFormat = TestExceptionFormat.FULL
            }

            useJUnitPlatform()
        }
    }

    tasks.named<JacocoReport>("jacocoTestReport") {
        reports {
            // Enable XML in addition to HTML for CI integration.
            xml.isEnabled = true
        }
    }

    tasks.register<JacocoReport>("jacocoFunTestReport") {
        description = "Generates code coverage report for the funTest task."
        group = "Reporting"

        executionData(funTest.get())
        sourceSets(sourceSets["main"])

        reports {
            // Enable XML in addition to HTML for CI integration.
            xml.isEnabled = true
        }
    }

    tasks.register("jacocoReport") {
        description = "Generates code coverage reports for all test tasks."
        group = "Reporting"

        dependsOn(tasks.withType<JacocoReport>())
    }

    tasks.named("check") {
        dependsOn(funTest)
    }

    val sourcesJar by tasks.registering(Jar::class) {
        archiveClassifier.set("sources")
        from(sourceSets["main"].allSource)
    }

    val dokka by tasks.existing(DokkaTask::class) {
        description = "Generates minimalistic HTML documentation, Java classes are translated to Kotlin."
    }

    val dokkaJar by tasks.registering(Jar::class) {
        dependsOn(dokka)

        description = "Assembles a jar archive containing the minimalistic HTML documentation."
        group = "Documentation"

        archiveClassifier.set("dokka")
        from(dokka.get().outputDirectory)
    }

    val dokkaJavadoc by tasks.registering(DokkaTask::class) {
        description = "Generates documentation that looks like normal Javadoc, Kotlin classes are translated to Java."
        outputFormat = "javadoc"
        outputDirectory = "$buildDir/javadoc"
    }

    val javadocJar by tasks.registering(Jar::class) {
        dependsOn(dokkaJavadoc)

        description = "Assembles a jar archive containing the Javadoc documentation."
        group = "Documentation"

        archiveClassifier.set("javadoc")
        from(dokkaJavadoc.get().outputDirectory)
    }

    configure<PublishingExtension> {
        publications {
            create<MavenPublication>(name) {
                groupId = "org.ossreviewtoolkit"

                from(components["java"])
                artifact(sourcesJar.get())
                artifact(javadocJar.get())

                pom {
                    licenses {
                        license {
                            name.set("Apache-2.0")
                            url.set("http://www.apache.org/licenses/LICENSE-2.0")
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

tasks.register("allDependencies") {
    val dependenciesTasks = allprojects.map { it.tasks.named("dependencies").get() }
    dependsOn(dependenciesTasks)

    dependenciesTasks.zipWithNext().forEach { (a, b) ->
        b.mustRunAfter(a)
    }
}
