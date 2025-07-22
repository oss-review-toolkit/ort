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

package org.ossreviewtoolkit.utils.ort

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

import kotlinx.coroutines.CoroutineScope

import org.apache.logging.log4j.kotlin.CoroutineThreadContext

/**
 * Global variable that gets toggled by a command line parameter parsed in the main entry points of the modules.
 */
var printStackTrace = false

/**
 * Print the stack trace of the [Throwable] if [printStackTrace] is set to true.
 */
fun Throwable.showStackTrace(): Unit = run { if (printStackTrace) printStackTrace() }

/**
 * A wrapper for [kotlinx.coroutines.runBlocking] which always adds a [CoroutineThreadContext] to the newly created
 * coroutine context. This ensures that Log4j's MDC context is not lost when `runBlocking` is used.
 *
 * This function should be used instead of [kotlinx.coroutines.runBlocking] in all code that can be used as a library to
 * preserve any MDC context set by a consumer.
 */
fun <T> runBlocking(context: CoroutineContext = EmptyCoroutineContext, block: suspend CoroutineScope.() -> T): T =
    @Suppress("ForbiddenMethodCall")
    kotlinx.coroutines.runBlocking(context + CoroutineThreadContext()) { block() }
