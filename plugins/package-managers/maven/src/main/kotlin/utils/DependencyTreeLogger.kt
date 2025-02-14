/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.maven.utils

import java.io.PrintStream

import org.apache.logging.log4j.kotlin.logger

import org.codehaus.plexus.logging.AbstractLogger

/**
 * A special logger implementation that can redirect the output of the Maven dependency tree plugin to a separate file.
 *
 * When running a Maven build programmatically, the format of the generated log output is determined by the logging
 * configuration of the client application. This also affects the log level and the log format. Because of this, it can
 * be problematic to detect the output of the Maven dependency tree plugin in the log output. This logger
 * implementation solves this problem by printing the log messages verbatim into a configured output stream.
 */
internal class DependencyTreeLogger(
    /** The stream where to direct log output to. */
    private val outputStream: PrintStream
) : AbstractLogger(LEVEL_DEBUG, "DependencyTreeLogger") {
    override fun getChildLogger(name: String?) = this

    override fun debug(message: String, throwable: Throwable?) = log(message, throwable)

    override fun error(message: String, throwable: Throwable?) = log(message, throwable)

    override fun fatalError(message: String, throwable: Throwable?) = log(message, throwable)

    override fun info(message: String, throwable: Throwable?) = log(message, throwable)

    override fun warn(message: String, throwable: Throwable?) = log(message, throwable)

    /**
     * Log the given [message] and optional [throwable] in the standard format defined by this logger.
     */
    private fun log(message: String, throwable: Throwable?) {
        outputStream.println(message)

        logger.info { "[DEPENDENCY TREE] $message" }
        throwable?.also {
            logger.error("[DEPENDENCY TREE ERROR]", it)
        }
    }
}
