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

import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm

import java.nio.charset.Charset
import java.nio.file.Files

import org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask

import org.gradle.accessors.dm.LibrariesForLibs

private val Project.libs: LibrariesForLibs
    get() = extensions.getByType()

plugins {
    // Apply core plugins.
    application
    signing

    // Apply precompiled plugins.
    id("ort-kotlin-conventions")
    id("ort-publication-conventions")

    // Apply third-party plugins.
    id("org.graalvm.buildtools.native")
}

application {
    applicationDefaultJvmArgs = listOf(
        "--add-opens", "java.base/java.io=ALL-UNNAMED",
        "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED"
    )
}

mavenPublishing {
    configure(KotlinJvm(JavadocJar.Dokka("dokkatooGeneratePublicationJavadoc")))
}

graalvmNative {
    toolchainDetection = true

    // For options see https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html.
    binaries {
        named("main") {
            imageName = provider { application.applicationName }

            val initializeAtBuildTime = listOf(
                "ch.qos.logback.classic.Level",
                "ch.qos.logback.classic.Logger",
                "ch.qos.logback.classic.LoggerContext",
                "ch.qos.logback.classic.PatternLayout",
                "ch.qos.logback.classic.encoder.PatternLayoutEncoder",
                "ch.qos.logback.classic.joran.JoranConfigurator",
                "ch.qos.logback.classic.model.ConfigurationModel",
                "ch.qos.logback.classic.model.LoggerModel",
                "ch.qos.logback.classic.model.RootLoggerModel",
                "ch.qos.logback.classic.model.processor.LoggerModelHandler",
                "ch.qos.logback.classic.model.processor.RootLoggerModelHandler",
                "ch.qos.logback.classic.pattern.DateConverter",
                "ch.qos.logback.classic.pattern.LevelConverter",
                "ch.qos.logback.classic.pattern.LineSeparatorConverter",
                "ch.qos.logback.classic.pattern.LoggerConverter",
                "ch.qos.logback.classic.pattern.MessageConverter",
                "ch.qos.logback.classic.pattern.NamedConverter\$CacheMissCalculator",
                "ch.qos.logback.classic.pattern.NamedConverter\$NameCache",
                "ch.qos.logback.classic.pattern.ThreadConverter",
                "ch.qos.logback.classic.pattern.ThrowableProxyConverter",
                "ch.qos.logback.classic.spi.LoggerContextVO",
                "ch.qos.logback.classic.spi.TurboFilterList",
                "ch.qos.logback.classic.util.ContextInitializer",
                "ch.qos.logback.classic.util.ContextInitializer\$1",
                "ch.qos.logback.classic.util.LogbackMDCAdapter",
                "ch.qos.logback.core.BasicStatusManager",
                "ch.qos.logback.core.ConsoleAppender",
                "ch.qos.logback.core.helpers.CyclicBuffer",
                "ch.qos.logback.core.joran.spi.ConfigurationWatchList",
                "ch.qos.logback.core.joran.spi.ConsoleTarget\$1",
                "ch.qos.logback.core.model.AppenderModel",
                "ch.qos.logback.core.model.AppenderRefModel",
                "ch.qos.logback.core.model.ImplicitModel",
                "ch.qos.logback.core.model.processor.AppenderModelHandler",
                "ch.qos.logback.core.model.processor.AppenderRefModelHandler",
                "ch.qos.logback.core.model.processor.DefaultProcessor",
                "ch.qos.logback.core.model.processor.ImplicitModelHandler",
                "ch.qos.logback.core.pattern.FormatInfo",
                "ch.qos.logback.core.pattern.LiteralConverter",
                "ch.qos.logback.core.spi.AppenderAttachableImpl",
                "ch.qos.logback.core.spi.ContextAwareImpl",
                "ch.qos.logback.core.spi.FilterAttachableImpl",
                "ch.qos.logback.core.spi.LogbackLock",
                "ch.qos.logback.core.status.InfoStatus",
                "ch.qos.logback.core.util.COWArrayList",
                "ch.qos.logback.core.util.CachingDateFormatter",
                "ch.qos.logback.core.util.CachingDateFormatter\$CacheTuple",
                "com.github.ajalt.mordant.internal.nativeimage.NativeImagePosixMppImpls",
                "org.apache.sshd.common.file.root.RootedFileSystemProvider"
            ).joinToString(separator = ",", prefix = "--initialize-at-build-time=")

            buildArgs.addAll(
                initializeAtBuildTime,
                "--report-unsupported-elements-at-runtime",
                "--parallelism=8",
                "-J-Xmx16g"
            )
        }
    }

    metadataRepository {
        enabled = true
    }
}

dependencies {
    implementation(libs.logbackClassic)

    runtimeOnly(libs.log4j.api.slf4j)
}

tasks.named<BuildNativeImageTask>("nativeCompile") {
    // Gradle's "Copy" task cannot handle symbolic links, see https://github.com/gradle/gradle/issues/3982. That is why
    // links contained in the GraalVM distribution archive get broken during provisioning and are replaced by empty
    // files. Address this by recreating the links in the toolchain directory.
    val toolchainDir = options.get().javaLauncher.get().executablePath.asFile.parentFile.run {
        if (name == "bin") parentFile else this
    }

    val toolchainFiles = toolchainDir.walkTopDown().filter { it.isFile }
    val emptyFiles = toolchainFiles.filter { it.length() == 0L }

    // Find empty toolchain files that are named like other toolchain files and assume these should have been links.
    val links = toolchainFiles.mapNotNull { file ->
        emptyFiles.singleOrNull { it != file && it.name == file.name }?.let {
            file to it
        }
    }

    // Fix up symbolic links.
    links.forEach { (target, link) ->
        logger.quiet("Fixing up '$link' to link to '$target'.")

        if (link.delete()) {
            Files.createSymbolicLink(link.toPath(), target.toPath())
        } else {
            logger.warn("Unable to delete '$link'.")
        }
    }
}

val jar by tasks.getting(Jar::class)

val pathingJar by tasks.registering(Jar::class) {
    archiveClassifier = "pathing"

    manifest {
        // Work around the command line length limit on Windows when passing the classpath to Java, see
        // https://github.com/gradle/gradle/issues/1989.
        attributes["Class-Path"] = configurations.runtimeClasspath.get().joinToString(" ") { it.name }
    }
}

tasks.named<CreateStartScripts>("startScripts") {
    classpath = jar.outputs.files + pathingJar.get().outputs.files

    doLast {
        // Append the plugin directory to the Windows classpath.
        val windowsScriptText = windowsScript.readText(Charset.defaultCharset())
        windowsScript.writeText(
            windowsScriptText.replace(
                Regex("(set CLASSPATH=%APP_HOME%\\\\lib\\\\.*)"), "$1;%APP_HOME%\\\\plugin\\\\*"
            )
        )

        // Append the plugin directory to the Unix classpath.
        val unixScriptText = unixScript.readText(Charset.defaultCharset())
        unixScript.writeText(
            unixScriptText.replace(
                Regex("(CLASSPATH=\\\$APP_HOME/lib/.*)"), "$1:\\\$APP_HOME/plugin/*"
            )
        )
    }
}

distributions {
    main {
        contents {
            from(pathingJar) {
                into("lib")
            }
        }
    }
}

val distTar = tasks.named<Tar>("distTar") {
    compression = Compression.GZIP
}

val distZip = tasks.named<Zip>("distZip")

signing {
    val signingInMemoryKey: String? by project
    val signingInMemoryKeyPassword: String? by project

    if (signingInMemoryKey != null && signingInMemoryKeyPassword != null) {
        useInMemoryPgpKeys(signingInMemoryKey, signingInMemoryKeyPassword)
        sign(distTar.get())
        sign(distZip.get())
    }
}

tasks.named<JavaExec>("run") {
    System.getenv("TERM")?.also {
        val mode = it.substringAfter('-', "16color")
        environment("FORCE_COLOR" to mode)
    }

    System.getenv("COLORTERM")?.also {
        environment("FORCE_COLOR" to it)
    }
}
