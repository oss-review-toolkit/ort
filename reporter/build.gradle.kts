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

plugins {
    // Apply core plugins.
    `java-library`
    `java-test-fixtures`
}

val generatedResourcesDir = file("$buildDir/generated-resources/main")
val copyWebAppTemplate by tasks.registering(Copy::class) {
    dependsOn(":reporter-web-app:yarnBuild")

    from(project(":reporter-web-app").file("build")) {
        include("scan-report-template.html")
    }

    into(generatedResourcesDir)
    outputs.cacheIf { true }
}

sourceSets.named("main") {
    output.dir(mapOf("builtBy" to copyWebAppTemplate), generatedResourcesDir)
}

repositories {
    exclusiveContent {
        forRepository {
            maven("https://jitpack.io")
        }

        filter {
            includeGroup("com.github.ralfstuckert.pdfbox-layout")
            includeGroup("com.github.everit-org.json-schema")
        }
    }
}

dependencies {
    api(project(":model"))

    implementation(project(":clients:fossid-webapp-client"))
    implementation(project(":downloader"))
    implementation(project(":utils:ort-utils"))
    implementation(project(":utils:scripting-utils"))
    implementation(project(":utils:spdx-utils"))

    implementation("org.jetbrains.kotlin:kotlin-scripting-common")
    implementation("org.jetbrains.kotlin:kotlin-scripting-jvm-host")

    implementation(libs.asciidoctorj)
    implementation(libs.asciidoctorjPdf)
    implementation(libs.commonsCompress)
    implementation(libs.flexmark)
    implementation(libs.freemarker)
    implementation(libs.jacksonDatatypeJsr310)
    implementation(libs.jacksonModuleKotlin)
    implementation(libs.kotlinxCoroutines)
    implementation(libs.kotlinxHtml)
    implementation(libs.poiOoxml)

    // This is required to not depend on the version of Apache Xalan bundled with the JDK. Otherwise, the formatting of
    // the HTML generated in StaticHtmlReporter is slightly different with different Java versions.
    implementation(libs.saxonHe)

    testImplementation(libs.mockk)

    // Only the Java plugin's built-in "test" source set automatically depends on the test fixtures.
    funTestImplementation(testFixtures(project))

    funTestImplementation(libs.kotestAssertionsJson)
}
