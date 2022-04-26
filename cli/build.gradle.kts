/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
 * Copyright (C) 2020-2021 Bosch.IO GmbH
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

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

import java.nio.charset.Charset

val cliktVersion: String by project
val config4kVersion: String by project
val exposedVersion: String by project
val graalVmVersion: String by project
val jacksonVersion: String by project
val hikariVersion: String by project
val kotestVersion: String by project
val kotlinxCoroutinesVersion: String by project
val log4jCoreVersion: String by project
val postgresVersion: String by project
val reflectionsVersion: String by project
val sw360ClientVersion: String by project
val greenMailVersion: String by project

plugins {
    // Apply core plugins.
    application

    // Apply third-party plugins.
    id("com.github.johnrengelman.shadow")
    id("com.palantir.graal")
}

application {
    applicationName = "ort"
    mainClass.set("org.ossreviewtoolkit.cli.OrtMainKt")
}

graal {
    graalVersion(graalVmVersion)
    javaVersion("17")

    option("--no-fallback")

    // Work-around for:
    // "com.oracle.graal.pointsto.constraints.UnresolvedElementException:
    //  Discovered unresolved type during parsing: android.os.Build$VERSION"
    option("--allow-incomplete-classpath")

    // Work-around for:
    // "Error: Classes that should be initialized at run time got initialized during image building"
    option("--initialize-at-build-time=org.jruby.util.RubyFileTypeDetector")

    // Work-around for:
    // "Unsupported method java.lang.invoke.MethodHandleNatives.setCallSiteTargetNormal() is reachable"
    option("--report-unsupported-elements-at-runtime")

    // Work-around for:
    // "Error: Non-reducible loop requires too much duplication"
    option("-H:MaxDuplicationFactor=3.0")

    mainClass("org.ossreviewtoolkit.cli.OrtMainKt")
    outputName("ort")
}

tasks.withType<ShadowJar> {
    isZip64 = true
}

tasks.named<CreateStartScripts>("startScripts").configure {
    doLast {
        // Work around the command line length limit on Windows when passing the classpath to Java, see
        // https://github.com/gradle/gradle/issues/1989#issuecomment-395001392.
        val windowsScriptText = windowsScript.readText(Charset.defaultCharset())
        windowsScript.writeText(
            windowsScriptText.replace(
                Regex("set CLASSPATH=%APP_HOME%\\\\lib\\\\.*"),
                "set CLASSPATH=%APP_HOME%\\\\lib\\\\*;%APP_HOME%\\\\plugin\\\\*"
            )
        )

        val unixScriptText = unixScript.readText(Charset.defaultCharset())
        unixScript.writeText(
            unixScriptText.replace(
                Regex("CLASSPATH=\\\$APP_HOME/lib/.*"),
                "CLASSPATH=\\\$APP_HOME/lib/*:\\\$APP_HOME/plugin/*"
            )
        )
    }
}

repositories {
    // Need to repeat several custom repository definitions of other submodules here, see
    // https://github.com/gradle/gradle/issues/4106.
    exclusiveContent {
        forRepository {
            maven("https://repo.gradle.org/gradle/libs-releases/")
        }

        filter {
            includeGroup("org.gradle")
        }
    }

    exclusiveContent {
        forRepository {
            maven("https://repo.eclipse.org/content/repositories/sw360-releases/")
        }

        filter {
            includeGroup("org.eclipse.sw360")
        }
    }

    exclusiveContent {
        forRepository {
            maven("https://jitpack.io/")
        }

        filter {
            includeGroup("com.github.ralfstuckert.pdfbox-layout")
            includeGroup("com.github.everit-org.json-schema")
        }
    }
    exclusiveContent {
        forRepository {
            maven("https://packages.atlassian.com/maven-external")
        }

        filter {
            includeGroupByRegex("com\\.atlassian\\..*")
            includeVersionByRegex("log4j", "log4j", ".*-atlassian-.*")
        }
    }
}

dependencies {
    implementation(project(":advisor"))
    implementation(project(":analyzer"))
    implementation(project(":downloader"))
    implementation(project(":evaluator"))
    implementation(project(":model"))
    implementation(project(":notifier"))
    implementation(project(":reporter"))
    implementation(project(":scanner"))
    implementation(project(":utils:core-utils"))
    implementation(project(":utils:spdx-utils"))

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.github.ajalt.clikt:clikt:$cliktVersion")
    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("io.github.config4k:config4k:$config4kVersion")
    implementation("org.apache.logging.log4j:log4j-core:$log4jCoreVersion")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:$log4jCoreVersion")
    implementation("org.eclipse.sw360:client:$sw360ClientVersion")
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposedVersion")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("org.reflections:reflections:$reflectionsVersion")

    testImplementation(project(":utils:test-utils"))

    testImplementation("com.icegreen:greenmail:$greenMailVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")

    funTestImplementation(sourceSets["main"].output)
    funTestImplementation(sourceSets["test"].output)
}

configurations["funTestImplementation"].extendsFrom(configurations["testImplementation"])
