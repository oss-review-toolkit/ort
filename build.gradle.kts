/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

import java.net.URL

import org.eclipse.jgit.api.Git

import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

import org.jetbrains.gradle.ext.Gradle
import org.jetbrains.gradle.ext.runConfigurations
import org.jetbrains.gradle.ext.settings
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.buildConfig)
    alias(libs.plugins.detekt)
    alias(libs.plugins.dokka)
    alias(libs.plugins.ideaExt)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.taskInfo)
    alias(libs.plugins.versionCatalogUpdate)
    alias(libs.plugins.versions)
}

buildscript {
    dependencies {
        classpath(libs.jgit)
    }
}

// Only override a default version (which usually is "unspecified"), but not a custom version.
if (version == Project.DEFAULT_VERSION) {
    version = Git.open(rootDir).use { git ->
        // Make the output exactly match "git describe --abbrev=10 --always --tags --dirty", which is what is used in
        // "scripts/docker_build.sh", to make the hash match what JitPack uses.
        val description = git.describe().setAbbrev(10).setAlways(true).setTags(true).call()
        val isDirty = git.status().call().hasUncommittedChanges()

        if (isDirty) "$description-dirty" else description
    }
}

logger.quiet("Building ORT version $version.")

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

allprojects {
    buildscript {
        repositories {
            mavenCentral()
        }
    }

    repositories {
        mavenCentral()
    }

    apply(plugin = "com.github.gmazzo.buildconfig")
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

    tasks.withType<Detekt>().configureEach detekt@{
        dependsOn(":detekt-rules:assemble")

        reports {
            html.required.set(false)
            sarif.required.set(true)
            txt.required.set(false)
            xml.required.set(false)
        }
    }
}

subprojects {
    version = rootProject.version

    if (name == "reporter-web-app") return@subprojects

    // Apply core plugins.
    apply(plugin = "jacoco")
    apply(plugin = "maven-publish")

    // Apply third-party plugins.
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.dokka")

    testing {
        suites {
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

    plugins.withType<JavaLibraryPlugin>().configureEach {
        dependencies {
            "testImplementation"(project(":utils:test-utils"))

            "testImplementation"(libs.kotestAssertionsCore)
            "testImplementation"(libs.kotestRunnerJunit5)
        }

        configurations["funTestImplementation"].extendsFrom(configurations["testImplementation"])
    }

    dependencies {
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${rootProject.libs.versions.kotlinPlugin.get()}")
    }

    configurations.all {
        // Do not tamper with configurations related to the detekt plugin, for some background information
        // https://github.com/detekt/detekt/issues/2501.
        if (!name.startsWith("detekt")) {
            resolutionStrategy {
                // Ensure all OkHttp versions match our version >= 4 to avoid Kotlin vs. Java issues with OkHttp 3.
                force(rootProject.libs.okhttp)

                // Ensure all API library versions match our core library version.
                force(rootProject.libs.log4jApi)

                // Ensure that all transitive versions of Kotlin libraries match our version of Kotlin.
                force("org.jetbrains.kotlin:kotlin-reflect:${rootProject.libs.versions.kotlinPlugin.get()}")
                force("org.jetbrains.kotlin:kotlin-script-runtime:${rootProject.libs.versions.kotlinPlugin.get()}")
            }
        }
    }

    tasks.withType<KotlinCompile>().configureEach {
        val customCompilerArgs = listOf(
            "-Xallow-result-return-type",
            "-opt-in=kotlin.contracts.ExperimentalContracts",
            "-opt-in=kotlin.io.path.ExperimentalPathApi",
            "-opt-in=kotlin.time.ExperimentalTime"
        )

        kotlinOptions {
            allWarningsAsErrors = true
            jvmTarget = javaVersion.majorVersion
            apiVersion = "1.7"
            freeCompilerArgs = freeCompilerArgs + customCompilerArgs
        }
    }

    tasks.dokkaHtml.configure {
        dokkaSourceSets {
            configureEach {
                jdkVersion.set(11)

                val jacksonVersion = libs.versions.jackson.get()
                val log4jVersion = libs.versions.log4j.get()

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
                    val majorVersion = log4jVersion.substringBefore('.')
                    val baseUrl = "https://logging.apache.org/log4j/$majorVersion.x/log4j-api/apidocs"
                    url.set(URL(baseUrl))
                    packageListUrl.set(URL("$baseUrl/package-list"))
                }
            }
        }
    }

    tasks.withType<Test>().configureEach {
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
        }

        useJUnitPlatform()
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
                groupId = "org.ossreviewtoolkit"

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
    val dependenciesTasks = allprojects.map { it.tasks.named("dependencies") }
    dependsOn(dependenciesTasks)

    // Ensure deterministic output by requiring to run tasks after each other in always the same order.
    dependenciesTasks.zipWithNext().forEach { (a, b) ->
        b.configure {
            mustRunAfter(a)
        }
    }
}
