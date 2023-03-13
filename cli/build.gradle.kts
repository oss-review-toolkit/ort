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

import java.nio.charset.Charset

@Suppress("DSL_SCOPE_VIOLATION") // See https://youtrack.jetbrains.com/issue/KTIJ-19369.
plugins {
    // Apply core plugins.
    application

    // Apply third-party plugins.
    alias(libs.plugins.graal)
}

application {
    applicationName = "ort"
    applicationDefaultJvmArgs = listOf(
        "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens", "java.base/java.io=ALL-UNNAMED"
    )
    mainClass.set("org.ossreviewtoolkit.cli.OrtMainKt")
}

graal {
    graalVersion(libs.versions.graal.get())
    javaVersion("17")

    // Build a standalone native executable or report a failure.
    option("--no-fallback")

    // Work-around for:
    // "WARNING: Unknown module: org.graalvm.nativeimage.llvm specified to --add-exports"
    option("-J--add-modules")
    option("-JALL-SYSTEM")

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
    implementation(project(":utils:ort-utils"))
    implementation(project(":utils:spdx-utils"))

    implementation(platform(project(":plugins:package-curation-providers")))
    implementation(platform(project(":plugins:package-managers")))

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation(libs.bundles.exposed)
    implementation(libs.bundles.kotlinxSerialization)
    implementation(libs.clikt)
    implementation(libs.hikari)
    implementation(libs.jacksonModuleKotlin)
    implementation(libs.kotlinxCoroutines)
    implementation(libs.log4jApiToSlf4j)
    implementation(libs.logbackClassic)
    implementation(libs.postgres)
    implementation(libs.reflections)
    implementation(libs.sw360Client)

    funTestImplementation(libs.greenmail)
}
