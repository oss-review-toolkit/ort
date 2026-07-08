/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.clitestlauncher

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.switch

import kotlin.system.exitProcess

import org.ossreviewtoolkit.utils.common.Os

import org.slf4j.LoggerFactory

/**
 * The entry point for the application with [args] being the list of arguments.
 */
fun main(args: Array<String>) {
    Os.fixupUserHomeProperty()
    TestMain(args).main(args)
    exitProcess(0)
}

class TestMain(private val originalArgv: Array<String>) : CliktCommand("ort-test-launcher") {
    private val logLevel by option(help = "Set the verbosity level of log output.").switch(
        "--error" to Level.ERROR,
        "--warn" to Level.WARN,
        "--info" to Level.INFO,
        "--debug" to Level.DEBUG
    ).default(Level.WARN)

    override fun run() {
        // This is somewhat dirty: ORT uses Log4j as the logging API (because of its nice Kotlin API), but Logback as
        // the implementation (for its robustness). The Log4j API does not provide a way to get the root logger.
        // However, the SLF4J API does, and knowing it is Logback the root level can be set. That is why ORT's CLI
        // additionally depends on the SLF4J API, just to be able to set the root log level.
        val rootLogger = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
        rootLogger.level = logLevel

        Os.fixupUserHomeProperty()

        if ("--specs" !in originalArgv) {
            io.kotest.engine.launcher.main(originalArgv + arrayOf("--specs", "scan"))
        } else {
            io.kotest.engine.launcher.main(originalArgv)
        }
    }
}
