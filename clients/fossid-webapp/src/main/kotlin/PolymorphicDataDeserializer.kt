/*
 * Copyright (C) 2020 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.clients.fossid

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.BeanProperty
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.deser.ContextualDeserializer
import com.fasterxml.jackson.databind.deser.std.StdDeserializer

/**
 * A custom JSON deserializer implementation to deal with inconsistencies in error responses sent by FossID
 * for requests returning a single value. If such a request fails, the response from FossID contains an
 * empty array for the value, which cannot be handled by the default deserialization.
 */
internal class PolymorphicDataDeserializer(val boundType: JavaType? = null) :
    StdDeserializer<PolymorphicData<Any>>(PolymorphicData::class.java), ContextualDeserializer {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): PolymorphicData<Any> {
        requireNotNull(boundType) {
            "The PolymorphicDataDeserializer needs a type to deserialize values!"
        }

        return when (p.currentToken) {
            JsonToken.START_ARRAY -> {
                val arrayType = ctxt.typeFactory.constructArrayType(boundType)
                val array = ctxt.readValue<Array<Any>>(p, arrayType)
                PolymorphicData(array.firstOrNull())
            }

            JsonToken.START_OBJECT -> {
                val data = ctxt.readValue<Any>(p, boundType)
                PolymorphicData(data)
            }

            else -> {
                val delegate = ctxt.findNonContextualValueDeserializer(boundType)
                PolymorphicData(delegate.deserialize(p, ctxt))
            }
        }
    }

    override fun createContextual(ctxt: DeserializationContext?, property: BeanProperty?): JsonDeserializer<*> {
        val type = property?.member?.type?.bindings?.getBoundType(0)
        return PolymorphicDataDeserializer(type)
    }
}
