import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

import com.here.ort.gradle.*

import io.gitlab.arturbosch.detekt.detekt

import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.gradle.ext.JUnit
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

import java.io.ByteArrayOutputStream
import java.net.URL

val detektPluginVersion: String by project
val kotlinPluginVersion: String by project

val kotlintestVersion: String by project

plugins {
    kotlin("jvm") apply false

    id("io.gitlab.arturbosch.detekt") apply false
    id("org.jetbrains.dokka") apply false

    id("com.github.ben-manes.versions")
    id("org.ajoberstar.reckon")
    id("org.jetbrains.gradle.plugin.idea-ext")
}

reckon {
    scopeFromProp()
    stageFromProp("beta", "rc", "final")
}

idea {
    project {
        settings {
            runConfigurations {
                defaults<JUnit> {
                    // Disable "condensed" multi-line diffs when running tests from the IDE to more easily accept actual results
                    // as expected results.
                    vmParameters = "-Dkotlintest.assertions.multi-line-diff=simple"
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

    resolutionStrategy {
        componentSelection {
            all {
                val nonFinalQualifiers = listOf("alpha", "b", "beta", "cr", "ea", "eap", "m", "pr", "preview", "rc")
                val isNonFinalVersion = nonFinalQualifiers.any { qualifier ->
                    candidate.version.matches(Regex("(?i).*[.-]$qualifier[.\\d-+]*"))
                }

                if (isNonFinalVersion) reject("Release candidate")
            }
        }
    }
}

subprojects {
    buildscript {
        repositories {
            jcenter()
        }
    }

    if (name == "reporter-web-app") return@subprojects

    // Apply core plugins.
    apply(plugin = "jacoco")
    apply(plugin = "maven-publish")

    // Apply third-party plugins.
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "io.gitlab.arturbosch.detekt")

    // Note: Kotlin DSL cannot directly access sourceSets that are created by applying a plugin in the very same
    // project, thus get the source set programmatically.
    val sourceSets = the<SourceSetContainer>()

    sourceSets.create("funTest") {
        withConvention(KotlinSourceSet::class) {
            kotlin.srcDirs("src/funTest/kotlin")
        }
    }

    repositories {
        jcenter()
    }

    // Note: Kotlin DSL cannot directly access configurations that are created by applying a plugin in the very same
    // project, thus put configuration names in quotes to leverage lazy lookup.

    dependencies {
        "detektPlugins"("io.gitlab.arturbosch.detekt:detekt-formatting:$detektPluginVersion")
    }

    plugins.withType<JavaLibraryPlugin> {
        dependencies {
            "api"("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

            "testImplementation"("io.kotlintest:kotlintest-core:$kotlintestVersion")
            "testImplementation"("io.kotlintest:kotlintest-assertions:$kotlintestVersion")
            "testImplementation"("io.kotlintest:kotlintest-runner-junit5:$kotlintestVersion")
            "testImplementation"(project(":test-utils"))

            "funTestImplementation"(sourceSets["main"].output)
        }

        configurations["funTestImplementation"].extendsFrom(configurations["testImplementation"])
    }

    configurations.all {
        resolutionStrategy {
            // Ensure that all transitive versions of "kotlin-reflect" match our version of "kotlin-stdlib".
            force("org.jetbrains.kotlin:kotlin-reflect:$kotlinPluginVersion")
        }
    }

    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions {
            allWarningsAsErrors = true
            jvmTarget = "1.8"
            apiVersion = "1.3"
        }
    }

    detekt {
        // Align the detekt core and plugin versions.
        toolVersion = detektPluginVersion

        config = files("../.detekt.yml")
        input = files("src/main/kotlin", "src/test/kotlin", "src/funTest/kotlin")
    }

    tasks.named<DokkaTask>("dokka") {
        jdkVersion = 8

        externalDocumentationLink {
            url = URL("https://codehaus-plexus.github.io/plexus-containers/plexus-container-default/apidocs/")
        }

        externalDocumentationLink {
            url = URL("https://fasterxml.github.io/jackson-databind/javadoc/2.9/")
        }

        externalDocumentationLink {
            url = URL("http://jakewharton.github.io/DiskLruCache/")
        }

        externalDocumentationLink {
            url = URL("https://logback.qos.ch/apidocs/")
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
                setEnabled(enabled)
            }

            systemProperties = listOf("kotlintest.tags.include", "kotlintest.tags.exclude").associateWith {
                System.getProperty(it)
            }

            testLogging {
                events = setOf(TestLogEvent.STARTED, TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
                setExceptionFormat(TestExceptionFormat.FULL)
            }

            useJUnitPlatform()
        }
    }

    tasks.named<JacocoReport>("jacocoTestReport") {
        reports {
            // Enable XML in addition to HTML for CI integration.
            xml.setEnabled(true)
        }
    }

    tasks.register<JacocoReport>("jacocoFunTestReport") {
        description = "Generates code coverage report for the funTest task."
        group = "Reporting"

        executionData(funTest.get())
        sourceSets(sourceSets["main"])

        reports {
            // Enable XML in addition to HTML for CI integration.
            xml.setEnabled(true)
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
                groupId = "com.here.ort"

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
                        connection.set("scm:git:https://github.com/heremaps/oss-review-toolkit.git")
                        developerConnection.set("scm:git:git@github.com:heremaps/oss-review-toolkit.git")
                        tag.set(version.toString())
                        url.set("https://github.com/heremaps/oss-review-toolkit")
                    }
                }
            }
        }
    }
}

val checkCopyright by tasks.registering(Exec::class) {
    description = "Checks for HERE Copyright headers in Kotlin files."
    group = "Verification"

    commandLine = listOf("git", "grep", "-EL", "Copyright \\(C\\) .+", "*.kt",
        ":!analyzer/src/funTest/assets/projects/external")
    setIgnoreExitValue(true)
    standardOutput = ByteArrayOutputStream()

    doLast {
        val output = standardOutput.toString().trim()
        if (output.isNotEmpty()) {
            throw GradleException("Please add copyright statements to the following Kotlin files:\n$output")
        }
    }
}

tasks.register("check") {
    description = "Runs all checks."
    group = "Verification"

    dependsOn(checkCopyright)
}
