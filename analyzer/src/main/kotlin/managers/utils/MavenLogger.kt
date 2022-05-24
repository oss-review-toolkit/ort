/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.analyzer.managers.utils

import org.apache.logging.log4j.Level

import org.codehaus.plexus.logging.AbstractLogger
import org.codehaus.plexus.logging.Logger

import org.ossreviewtoolkit.utils.ort.log

/**
 * Map a Log4j2 Level to a Plexus Logger level.
 */
private fun toPlexusLoggerLevel(level: Level) =
    when (level) {
        Level.OFF -> Logger.LEVEL_DISABLED
        Level.ERROR -> Logger.LEVEL_ERROR
        Level.WARN -> Logger.LEVEL_WARN
        Level.INFO -> Logger.LEVEL_INFO
        Level.DEBUG -> Logger.LEVEL_DEBUG
        Level.TRACE -> Logger.LEVEL_DEBUG
        Level.ALL -> Logger.LEVEL_DEBUG
        else -> Logger.LEVEL_DEBUG
    }

/**
 * Implementation of the Plexus [Logger] that forwards all logs to the [org.slf4j.Logger] [log] using the appropriate
 * log levels.
 */
class MavenLogger(level: Level) : AbstractLogger(toPlexusLoggerLevel(level), "MavenLogger") {
    override fun getChildLogger(name: String?) = this

    override fun debug(message: String, throwable: Throwable?) = log.debug(message, throwable)

    override fun error(message: String, throwable: Throwable?) = log.error(message, throwable)

    override fun fatalError(message: String, throwable: Throwable?) = log.error(message, throwable)

    override fun info(message: String, throwable: Throwable?) = log.info(message, throwable)

    override fun warn(message: String, throwable: Throwable?) = log.warn(message, throwable)
}
