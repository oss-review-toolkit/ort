import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

import io.gitlab.arturbosch.detekt.detekt

import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.plugins.ide.idea.model.IdeaProject

import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.gradle.ext.DefaultRunConfigurationContainer
import org.jetbrains.gradle.ext.JUnit
import org.jetbrains.gradle.ext.ProjectSettings
import org.jetbrains.gradle.ext.RunConfiguration
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
    id("org.ajoberstar.grgit")
    id("org.jetbrains.gradle.plugin.idea-ext")
}

// The following extension functions add the missing Kotlin DSL syntactic sugar for configuring the idea-ext plugin.

fun Project.idea(block: IdeaModel.() -> Unit) =
    (this as ExtensionAware).extensions.configure("idea", block)

fun IdeaProject.settings(block: ProjectSettings.() -> Unit) =
    (this@settings as ExtensionAware).extensions.configure(block)

fun ProjectSettings.runConfigurations(block: DefaultRunConfigurationContainer.() -> Unit) =
    (this@runConfigurations as ExtensionAware).extensions.configure("runConfigurations", block)

inline fun <reified T: RunConfiguration> DefaultRunConfigurationContainer.defaults(noinline block: T.() -> Unit) =
    defaults(T::class.java, block)

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

if (hasProperty("buildScan")) {
    // Note: Kotlin DSL cannot directly access extensions that are created by conditionally applied plugins, thus do the
    // lookup by name / via reflection.
    extensions.configure("buildScan") {
        val setTermsOfServiceUrl = javaClass.getMethod("setTermsOfServiceUrl", String::class.java)
        setTermsOfServiceUrl.invoke(this, "https://gradle.com/terms-of-service")

        val setTermsOfServiceAgree = javaClass.getMethod("setTermsOfServiceAgree", String::class.java)
        setTermsOfServiceAgree.invoke(this, "yes")
    }
}

tasks.named<DependencyUpdatesTask>("dependencyUpdates") {
    gradleReleaseChannel = "current"

    resolutionStrategy {
        componentSelection {
            all {
                val isNonFinalVersion = listOf("alpha", "beta", "rc", "cr", "m", "preview", "b", "ea").any { qualifier ->
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
            "funTestImplementation"(sourceSets["test"].output)
        }

        configurations["funTestImplementation"].extendsFrom(configurations["testImplementation"])
        configurations["funTestRuntime"].extendsFrom(configurations["testRuntime"])
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

    val dokka by tasks.existing(DokkaTask::class)

    val dokkaJavadoc by tasks.registering(DokkaTask::class) {
        outputFormat = "javadoc"
        outputDirectory = "$buildDir/javadoc"
    }

    val sourcesJar by tasks.registering(Jar::class) {
        classifier = "sources"
        from(sourceSets["main"].allSource)
    }

    val dokkaJar by tasks.registering(Jar::class) {
        dependsOn(dokka)
        classifier = "dokka"
        from(dokka.get().outputDirectory)
    }

    val javadocJar by tasks.registering(Jar::class) {
        dependsOn(dokkaJavadoc)
        classifier = "javadoc"
        from(dokkaJavadoc.get().outputDirectory)
    }

    artifacts {
        add("archives", sourcesJar)
        add("archives", dokkaJar)
        add("archives", javadocJar)
    }
}

val checkCopyright by tasks.registering(Exec::class) {
    description = "Checks for HERE Copyright headers in Kotlin files."
    group = "Verification"

    commandLine = listOf("git", "grep", "-EL", "Copyright.+HERE", "*.kt",
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
