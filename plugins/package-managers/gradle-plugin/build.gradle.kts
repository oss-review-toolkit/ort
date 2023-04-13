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

plugins {
    `java-gradle-plugin`
}

gradlePlugin {
    plugins.register("OrtModelPlugin") {
        id = "org.ossreviewtoolkit.plugins.packagemanagers.gradle.plugin"
        implementationClass = "org.ossreviewtoolkit.plugins.packagemanagers.gradleplugin.OrtModelPlugin"
    }
}

dependencies {
    api(project(":plugins:package-managers:gradle-model"))

    api(libs.mavenModel)
    api(libs.mavenModelBuilder)
}

tasks.register("fatJar", Jar::class) {
    description = "Creates a fat JAR that includes all required runtime dependencies."
    group = "Build"

    archiveBaseName.set("${project.name}-fat")

    manifest.from(tasks.jar.get().manifest)

    val classpath = configurations.runtimeClasspath.get().filter {
        // Only bundle files that are not specific to the Gradle version.
        it.isFile && !("gradle" in it.path && gradle.gradleVersion in it.path)
    }.mapNotNull {
        when (it.extension) {
            "class" -> it
            "jar" -> zipTree(it)
            else -> null
        }
    }

    from(classpath) {
        exclude("META-INF/*.DSA")
        exclude("META-INF/*.RSA")
        exclude("META-INF/*.SF")
    }

    with(tasks.jar.get())

    doLast {
        val pluginFatJar = zipTree(outputs.files.singleFile)
        val pluginClasses = pluginFatJar.files.map { it.invariantSeparatorsPath }
        val commonPath = pluginClasses.reduce { acc, s -> acc.commonPrefixWith(s) }
        val rootClasses = pluginClasses.filter { "/" !in it.removePrefix(commonPath) && it.endsWith(".class") }

        val expectedClassCount = 5
        val actualClassCount = rootClasses.size
        if (actualClassCount != expectedClassCount) {
            throw GradleException("The gradle-plugin JAR contains $actualClassCount instead of the expected " +
                    "$expectedClassCount classes in the root.")
        }
    }
}
