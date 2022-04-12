/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
 * Copyright (C) 2020 Bosch.IO GmbH
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

val apachePoiVersion: String by project
val asciidoctorjVersion: String by project
val asciidoctorjPdfVersion: String by project
val commonsCompressVersion: String by project
val cyclonedxCoreJavaVersion: String by project
val flexmarkVersion: String by project
val freemarkerVersion: String by project
val hamcrestCoreVersion: String by project
val jacksonVersion: String by project
val kotlinxCoroutinesVersion: String by project
val kotlinxHtmlVersion: String by project
val mockkVersion: String by project
val retrofitVersion: String by project
val saxonHeVersion: String by project
val simpleExcelVersion: String by project

plugins {
    // Apply core plugins.
    `java-library`
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

    implementation(project(":downloader"))
    implementation(project(":utils:core-utils"))
    implementation(project(":utils:spdx-utils"))

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")
    implementation("com.squareup.retrofit2:retrofit:$retrofitVersion")
    implementation("com.vladsch.flexmark:flexmark:$flexmarkVersion")
    implementation("org.apache.commons:commons-compress:$commonsCompressVersion")
    implementation("org.apache.poi:poi-ooxml:$apachePoiVersion")
    implementation("org.asciidoctor:asciidoctorj:$asciidoctorjVersion")
    implementation("org.asciidoctor:asciidoctorj-pdf:$asciidoctorjPdfVersion")
    implementation("org.cyclonedx:cyclonedx-core-java:$cyclonedxCoreJavaVersion")
    implementation("org.freemarker:freemarker:$freemarkerVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$kotlinxCoroutinesVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:$kotlinxHtmlVersion")

    // This is required to not depend on the version of Apache Xalan bundled with the JDK. Otherwise the formatting of
    // the HTML generated in StaticHtmlReporter is slightly different with different Java versions.
    implementation("net.sf.saxon:Saxon-HE:$saxonHeVersion")

    testImplementation("io.mockk:mockk:$mockkVersion")
}
