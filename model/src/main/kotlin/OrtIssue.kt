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

package org.ossreviewtoolkit.model

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.StdSerializer

import java.time.Instant

import org.ossreviewtoolkit.utils.common.normalizeLineBreaks
import org.ossreviewtoolkit.utils.ort.log
import org.ossreviewtoolkit.utils.ort.logOnce

/**
 * An issue that occurred while executing ORT.
 */
data class OrtIssue(
    /**
     * The timestamp of the issue.
     */
    val timestamp: Instant = Instant.now(),

    /**
     * A description of the issue source, e.g. the tool that caused the issue.
     */
    val source: String,

    /**
     * The issue's message.
     */
    @JsonSerialize(using = NormalizeLineBreaksSerializer::class)
    val message: String,

    /**
     * The issue's severity.
     */
    val severity: Severity = Severity.ERROR
) {
    override fun toString(): String {
        val time = if (timestamp == Instant.EPOCH) "Unknown time" else timestamp.toString()
        return "$time [$severity]: $source - $message"
    }
}

class NormalizeLineBreaksSerializer : StdSerializer<String>(String::class.java) {
    override fun serialize(value: String, gen: JsonGenerator, provider: SerializerProvider) {
        gen.writeString(value.normalizeLineBreaks())
    }
}

/**
 * Create an [OrtIssue] and [log] the message. The log level is aligned with the [severity].
 */
inline fun <reified T : Any> T.createAndLogIssue(
    source: String,
    message: String,
    severity: Severity? = null
): OrtIssue {
    val issue = severity?.let { OrtIssue(source = source, message = message, severity = it) }
        ?: OrtIssue(source = source, message = message)
    logOnce(issue.severity.toLog4jLevel()) { message }
    return issue
}
