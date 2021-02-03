/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
 * Copyright (C) 2020 Bosch.IO GmbH
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

val antennaVersion: String by project
val cliktVersion: String by project
val config4kVersion: String by project
val jacksonVersion: String by project
val kotestVersion: String by project
val kotlinxCoroutinesVersion: String by project
val log4jCoreVersion: String by project
val postgresVersion: String by project
val reflectionsVersion: String by project

plugins {
    // Apply core plugins.
    application
}

application {
    applicationName = "ort"
    mainClassName = "org.ossreviewtoolkit.OrtMainKt"
}

tasks.named<CreateStartScripts>("startScripts") {
    doLast {
        // Work around the command line length limit on Windows when passing the classpath to Java, see
        // https://github.com/gradle/gradle/issues/1989#issuecomment-395001392.
        windowsScript.writeText(windowsScript.readText().replace(Regex("set CLASSPATH=.*"),
            "set CLASSPATH=%APP_HOME%\\\\lib\\\\*"))
    }
}

val fatJar by tasks.registering(Jar::class) {
    description = "Creates a fat jar that includes all required dependencies."
    group = "Build"

    archiveBaseName.set(application.applicationName)

    manifest.from(tasks.jar.get().manifest)
    manifest {
        attributes["Main-Class"] = application.mainClassName
    }

    isZip64 = true

    val classpath = configurations.runtimeClasspath.get().filterNot {
        it.isFile && it.extension == "pom"
    }.map {
        if (it.isDirectory) it else zipTree(it)
    }

    from(classpath) {
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
        exclude("META-INF/*.SF")
    }

    with(tasks.jar.get())
}

repositories {
    // Need to repeat several custom repository definitions of other submodules here, see
    // https://github.com/gradle/gradle/issues/4106.
    exclusiveContent {
        forRepository {
            maven("https://repo.gradle.org/gradle/libs-releases-local/")
        }

        filter {
            includeGroup("org.gradle")
        }
    }

    exclusiveContent {
        forRepository {
            maven("https://download.eclipse.org/antenna/releases/")
        }

        filter {
            includeGroup("org.eclipse.sw360.antenna")
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
}

dependencies {
    implementation(project(":advisor"))
    implementation(project(":analyzer"))
    implementation(project(":downloader"))
    implementation(project(":evaluator"))
    implementation(project(":model"))
    implementation(project(":reporter"))
    implementation(project(":scanner"))
    implementation(project(":utils"))

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.github.ajalt.clikt:clikt:$cliktVersion")
    implementation("org.apache.logging.log4j:log4j-core:$log4jCoreVersion")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:$log4jCoreVersion")
    implementation("org.eclipse.sw360.antenna:sw360-client:$antennaVersion")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")
    implementation("org.postgresql:postgresql:$postgresVersion")
    implementation("org.reflections:reflections:$reflectionsVersion")

    testImplementation(project(":test-utils"))

    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")

    funTestImplementation(sourceSets["main"].output)
    funTestImplementation(sourceSets["test"].output)
}

configurations["funTestImplementation"].extendsFrom(configurations.testImplementation.get())
configurations["funTestRuntime"].extendsFrom(configurations.testRuntime.get())
