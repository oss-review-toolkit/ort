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
 * A class to modify the standard Jackson deserialization to deal with inconsistencies in responses
 * sent by the FossID server.
 * FossID usually returns data as a List or Map, but in case of no entries it returns a Boolean (which is set to
 * false). This custom deserializer streamlines the result:
 * - maps are converted to lists by ignoring the keys
 * - empty list is returned when the result is Boolean
 * - to address a FossID bug in get_all_scans operation, arrays are converted to list.
 */
internal class PolymorphicListDeserializer(val boundType: JavaType? = null) :
    StdDeserializer<PolymorphicList<Any>>(PolymorphicList::class.java), ContextualDeserializer {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): PolymorphicList<Any> {
        requireNotNull(boundType) {
            "The PolymorphicListDeserializer needs a type to deserialize values!"
        }

        return when (p.currentToken) {
            JsonToken.VALUE_FALSE -> PolymorphicList()

            JsonToken.START_ARRAY -> {
                val arrayType = ctxt.typeFactory.constructArrayType(boundType)
                val array = ctxt.readValue<Array<Any>>(p, arrayType)
                PolymorphicList(array.toList())
            }

            JsonToken.START_OBJECT -> {
                val mapType = ctxt.typeFactory.constructMapType(
                    LinkedHashMap::class.java,
                    String::class.java,
                    boundType.rawClass
                )
                val map = ctxt.readValue<Map<Any, Any>>(p, mapType)

                // Only keep the map's values: If the FossID functions which return a PolymorphicList return a
                // map, it always is the list of elements grouped by id. Since the ids are also present in the
                // elements themselves, no information is lost by discarding the keys.
                PolymorphicList(map.values.toList())
            }

            else -> error("FossID returned a type not handled by this deserializer!")
        }
    }

    override fun createContextual(ctxt: DeserializationContext?, property: BeanProperty?): JsonDeserializer<*> {
        // Extract the type from the property, i.e. the T in PolymorphicList.data<T>
        val type = property?.member?.type?.bindings?.getBoundType(0)
        return PolymorphicListDeserializer(type)
    }
}
