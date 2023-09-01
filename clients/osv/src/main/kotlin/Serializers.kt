/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.clients.osv

import java.time.Instant
import java.time.format.DateTimeFormatter

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Use a custom serializer to transform the legacy single-field events into newer typed events.
 */
internal object EventListSerializer : JsonTransformingSerializer<List<Event>>(ListSerializer(Event.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        element.jsonArray.map {
            val event = it.jsonObject
            when (event.entries.size) {
                1 -> {
                    val (type, value) = event.entries.first()
                    JsonObject(mapOf("type" to JsonPrimitive(type.uppercase()), "value" to value))
                }
                else -> event
            }
        }.let { JsonArray(it) }
}

/**
 * Handle RFC3339 formatted strings with a 'Z' as suffix.
 */
internal object InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(javaClass.canonicalName, PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Instant {
        return DateTimeFormatter.ISO_ZONED_DATE_TIME.parse(decoder.decodeString(), Instant::from)
    }
}
