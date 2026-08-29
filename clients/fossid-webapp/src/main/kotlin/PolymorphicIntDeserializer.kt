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
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.deser.std.StdDeserializer

/**
 * A class to modify the standard Jackson deserialization to deal with inconsistencies in responses
 * sent by the FossID server.
 * When deleting a scan, FossID returns the scan id as a string in the `data` property of the response. If no
 * scan could be found, it returns an empty array. Starting with FossID version 2023.1, the return type of the
 * [deleteScan] function is now a map of strings to strings. Creating a special [FossIdServiceWithVersion]
 * implementation for this call is an overkill as ORT does not even use the return value. Therefore, this change
 * is also handled by the [PolymorphicIntDeserializer].
 * This custom deserializer streamlines the result: everything is converted to Int and empty array is converted
 * to `null`. This deserializer also accepts primitive integers and arrays containing integers and maps of
 * strings to strings containing a single entry with an integer value.
 */
internal class PolymorphicIntDeserializer :
    StdDeserializer<PolymorphicInt>(PolymorphicInt::class.java) {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): PolymorphicInt =
        when (p.currentToken) {
            JsonToken.VALUE_STRING -> {
                val value = ctxt.readValue(p, String::class.java)
                PolymorphicInt(value.toInt())
            }

            JsonToken.VALUE_NUMBER_INT -> {
                val value = ctxt.readValue(p, Int::class.java)
                PolymorphicInt(value)
            }

            JsonToken.START_ARRAY -> {
                val array = ctxt.readValue(p, IntArray::class.java)
                val value = if (array.isEmpty()) null else array.first()
                PolymorphicInt(value)
            }

            JsonToken.START_OBJECT -> {
                val mapType = ctxt.typeFactory.constructMapType(
                    LinkedHashMap::class.java,
                    String::class.java,
                    String::class.java
                )
                val map = ctxt.readValue<Map<Any, Any>>(p, mapType)
                if (map.size != 1) {
                    error("A map representing a polymorphic integer should have one value!")
                }

                PolymorphicInt(map.values.first().toString().toInt())
            }

            else -> error("FossID returned a type not handled by this deserializer!")
        }
}
