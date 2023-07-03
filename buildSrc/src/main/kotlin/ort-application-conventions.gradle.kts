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

import java.nio.charset.Charset
import java.nio.file.Files

import org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask

plugins {
    // Apply core plugins.
    application

    // Apply precompiled plugins.
    id("ort-kotlin-conventions")

    // Apply third-party plugins.
    id("org.graalvm.buildtools.native")
}

application {
    applicationDefaultJvmArgs = listOf(
        "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens", "java.base/java.io=ALL-UNNAMED"
    )
}

graalvmNative {
    toolchainDetection = true

    // For options see https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html.
    binaries {
        named("main") {
            imageName = application.applicationName

            val initializeAtBuildTime = listOf(
                "ch.qos.logback.classic.Level",
                "ch.qos.logback.classic.Logger",
                "ch.qos.logback.classic.PatternLayout",
                "ch.qos.logback.core.CoreConstants",
                "ch.qos.logback.core.status.InfoStatus",
                "ch.qos.logback.core.status.StatusBase",
                "ch.qos.logback.core.util.Loader",
                "ch.qos.logback.core.util.StatusPrinter",
                "org.apache.sshd.sftp.client.fs.SftpFileSystemProvider",
                "org.slf4j.LoggerFactory"
            ).joinToString(separator = ",", prefix = "--initialize-at-build-time=")

            buildArgs.add(initializeAtBuildTime)
        }
    }

    metadataRepository {
        enabled = true
    }
}

// JDK 20 will only be supported starting with GraalVM 23, see
// https://www.graalvm.org/release-notes/release-calendar/#planned-releases
if (JavaVersion.current().majorVersion.toInt() <= 19) {
    tasks.named<BuildNativeImageTask>("nativeCompile") {
        // Gradle's "Copy" task cannot handle symbolic links, see https://github.com/gradle/gradle/issues/3982. That is
        // why links contained in the GraalVM distribution archive get broken during provisioning and are replaced by
        // empty files. Address this by recreating the links in the toolchain directory.
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
}

tasks.named<CreateStartScripts>("startScripts") {
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
