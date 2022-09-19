/*
 * Copyright (C) 2022 Bosch.IO GmbH
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

package org.ossreviewtoolkit.clients.clearlydefined

import java.io.File
import java.net.URI

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/**
 * A (de-)serializer for ClearlyDefined [Coordinates] instances from / to strings, or from a nested structure.
 */
object CoordinatesSerializer : KSerializer<Coordinates> by toStringSerializer(::Coordinates) {
    override fun deserialize(decoder: Decoder): Coordinates {
        require(decoder is JsonDecoder)
        return when (val element = decoder.decodeJsonElement()) {
            is JsonPrimitive -> Coordinates(element.content)
            is JsonObject -> Coordinates(
                ComponentType.fromString(element.getValue("type").jsonPrimitive.content),
                Provider.fromString(element.getValue("provider").jsonPrimitive.content),
                element["namespace"]?.jsonPrimitive?.content,
                element.getValue("name").jsonPrimitive.content,
                element["revision"]?.jsonPrimitive?.content
            )
            else -> throw IllegalArgumentException("Unsupported JSON element $element.")
        }
    }
}

/**
 * A (de-)serializer for Java [File] instances from / to strings.
 */
object FileSerializer : KSerializer<File> by toStringSerializer(::File)

/**
 * A (de-)serializer for Java [URI] instances class from / to strings.
 */
object URISerializer : KSerializer<URI> by toStringSerializer(::URI)

/**
 * A convenience function for creating a [ToStringSerializer] whose name is derived from the class name.
 */
inline fun <reified T : Any> toStringSerializer(noinline create: (String) -> T): ToStringSerializer<T> =
    ToStringSerializer(T::class.java.name, create)

/**
 * A serializer with the given [serialName] that uses [Any] instance's [toString] function for serialization and the
 * given [create] function for deserialization.
 */
class ToStringSerializer<T : Any>(serialName: String, private val create: (String) -> T) : KSerializer<T> {
    override val descriptor = PrimitiveSerialDescriptor(serialName, PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: T) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder) = create(decoder.decodeString())
}
