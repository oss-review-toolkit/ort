/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.deser.std.StdDeserializer

import java.time.Instant

/**
 * An error that occured while executing ORT.
 */
data class Error(
        /**
         * The timestamp of the error.
         */
        val timestamp: Instant = Instant.now(),

        /**
         * A description of the error source, e.g. the tool that caused the error.
         */
        val source: String,

        /**
         * The error message.
         */
        val message: String,

        /**
         * A flag to indicate whether this error should be excluded. This is set based on the .ort.yml configuration
         * file.
         */
        @JsonInclude(JsonInclude.Include.NON_DEFAULT)
        val excluded: Boolean = false
) {
    override fun toString() = "${if (timestamp == Instant.EPOCH) "n/a" else timestamp.toString()}: $source - $message"
}

class ErrorDeserializer : StdDeserializer<Error>(Error::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Error {
        val node = p.codec.readTree<JsonNode>(p)
        return if (node.isTextual) {
            // For backward-compatibility if only an error string is specified.
            Error(Instant.EPOCH, "", node.textValue())
        } else {
            Error(Instant.parse(node.get("timestamp").textValue()), node.get("source").textValue(),
                    node.get("message").textValue())
        }
    }
}
