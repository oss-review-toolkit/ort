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

package org.ossreviewtoolkit.utils.common

import java.io.File

/**
 * Extract the resource with the specified [name] to the given [target] file and return it.
 */
fun extractResource(name: String, target: File) =
    target.apply {
        val resource = checkNotNull(object {}.javaClass.getResource(name)) {
            "Resource '$name' not found."
        }

        parentFile.safeMkdirs()

        resource.openStream().use { inputStream ->
            outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }

/**
 * Temporarily set the specified system [properties] while executing [block]. Afterwards, previously set properties have
 * their original values restored and previously unset properties are cleared.
 */
fun <R> temporaryProperties(vararg properties: Pair<String, String?>, block: () -> R): R {
    val originalProperties = mutableListOf<Pair<String, String?>>()

    properties.forEach { (key, value) ->
        originalProperties += key to System.getProperty(key)
        value?.also { System.setProperty(key, it) } ?: System.clearProperty(key)
    }

    return try {
        block()
    } finally {
        originalProperties.forEach { (key, value) ->
            value?.also { System.setProperty(key, it) } ?: System.clearProperty(key)
        }
    }
}

/**
 * Call [block] only if the receiver is null, e.g. for error handling, and return the receiver in any case.
 */
inline fun <T> T.alsoIfNull(block: (T) -> Unit): T = this ?: also(block)

/**
 * Recursively collect the messages of this [Throwable] and all its causes and join them to a single [String].
 */
fun Throwable.collectMessages(): String {
    fun Throwable.formatCauseAndSuppressedMessages(): String? =
        buildString {
            cause?.also {
                appendLine("Caused by: ${it.javaClass.simpleName}: ${it.message}")
                it.formatCauseAndSuppressedMessages()?.prependIndent()?.also(::append)
            }

            suppressed.forEach {
                appendLine("Suppressed: ${it.javaClass.simpleName}: ${it.message}")
                it.formatCauseAndSuppressedMessages()?.prependIndent()?.also(::append)
            }
        }.trim().takeUnless { it.isEmpty() }

    return listOfNotNull(
        "${javaClass.simpleName}: $message",
        formatCauseAndSuppressedMessages()
    ).joinToString("\n")
}
