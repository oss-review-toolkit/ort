/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package com.here.ort.model

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.databind.ser.std.StdSerializer

import com.here.ort.utils.normalizeLineBreaks

import java.time.Instant

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
        val message: String,

        /**
         * The issue's severity.
         */
        val severity: Severity = Severity.ERROR
) {
    override fun toString() = "${if (timestamp == Instant.EPOCH) "n/a" else timestamp.toString()}: $source - $message"
}

class OrtIssueDeserializer : StdDeserializer<OrtIssue>(OrtIssue::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): OrtIssue {
        val node = p.codec.readTree<JsonNode>(p)
        return if (node.isTextual) {
            // For backward-compatibility if only an error string is specified.
            OrtIssue(Instant.EPOCH, "", node.textValue())
        } else {
            OrtIssue(
                    timestamp = Instant.parse(node.get("timestamp").textValue()),
                    source = node.get("source").textValue(),
                    message = node.get("message").textValue(),
                    severity = node.get("severity")?.let { Severity.valueOf(it.textValue()) } ?: Severity.ERROR
            )
        }
    }
}

class OrtIssueSerializer : StdSerializer<OrtIssue>(OrtIssue::class.java) {
    override fun serialize(value: OrtIssue, gen: JsonGenerator, provider: SerializerProvider) {
        gen.writeStartObject()
        gen.writeObjectField("timestamp", value.timestamp)
        gen.writeStringField("source", value.source)
        gen.writeStringField("message", value.message.normalizeLineBreaks())
        gen.writeStringField("severity", value.severity.name)
        gen.writeEndObject()
    }
}
