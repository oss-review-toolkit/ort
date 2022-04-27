/*
 * Copyright (C) 2020 Bosch.IO GmbH
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

@file:Suppress("TooManyFunctions")

package org.ossreviewtoolkit.clients.fossid

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.BeanProperty
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.deser.ContextualDeserializer
import com.fasterxml.jackson.databind.deser.std.StdDeserializer
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule

import okhttp3.OkHttpClient
import okhttp3.ResponseBody

import org.ossreviewtoolkit.clients.fossid.model.Project
import org.ossreviewtoolkit.clients.fossid.model.Scan
import org.ossreviewtoolkit.clients.fossid.model.identification.identifiedFiles.IdentifiedFile
import org.ossreviewtoolkit.clients.fossid.model.identification.ignored.IgnoredFile
import org.ossreviewtoolkit.clients.fossid.model.identification.markedAsIdentified.MarkedAsIdentifiedFile
import org.ossreviewtoolkit.clients.fossid.model.result.FossIdScanResult
import org.ossreviewtoolkit.clients.fossid.model.rules.IgnoreRule
import org.ossreviewtoolkit.clients.fossid.model.status.DownloadStatus
import org.ossreviewtoolkit.clients.fossid.model.status.ScanDescription
import org.ossreviewtoolkit.clients.fossid.model.status.ScanDescription2021dot2

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface FossIdRestService {
    companion object {
        /**
         * The mapper for JSON (de-)serialization used by this service.
         */
        val JSON_MAPPER: ObjectMapper = jsonMapper {
            propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            // FossID has a bug in get_results/scan.
            // Sometimes the match_type is "ignored", sometimes it is "Ignored".
            enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            addModule(
                kotlinModule().addDeserializer(PolymorphicList::class.java, PolymorphicListDeserializer())
            )
        }

        /**
         * A class to modify the standard Jackson deserialization to deal with inconsistencies in responses
         * sent by the FossID server.
         * FossID usually returns data as a List or Map, but in case of no entries it returns a Boolean (which is set to
         * false). This custom deserializer streamLines the result:
         * - maps are converted to lists by ignoring the keys
         * - empty list is returned when the result is Boolean
         * - to address a FossID bug in get_all_scans operation, arrays are converted to list.
         */
        private class PolymorphicListDeserializer(val boundType: JavaType? = null) :
            StdDeserializer<PolymorphicList<Any>>(PolymorphicList::class.java), ContextualDeserializer {
            override fun deserialize(p: JsonParser, ctxt: DeserializationContext): PolymorphicList<Any> {
                requireNotNull(boundType) {
                    "The PolymorphicListDeserializer needs a type to deserialize values!"
                }
                return when (p.currentToken) {
                    JsonToken.VALUE_FALSE -> PolymorphicList()
                    JsonToken.START_ARRAY -> {
                        val arrayType = JSON_MAPPER.typeFactory.constructArrayType(boundType)
                        val array = JSON_MAPPER.readValue<Array<Any>>(p, arrayType)
                        PolymorphicList(array.toList())
                    }
                    JsonToken.START_OBJECT -> {
                        val mapType = JSON_MAPPER.typeFactory.constructMapType(
                            LinkedHashMap::class.java,
                            String::class.java,
                            boundType.rawClass
                        )
                        val map = JSON_MAPPER.readValue<Map<Any, Any>>(p, mapType)
                        // we keep only the values of the map: when the FossID functions which return a PolymorphicList
                        // return a map, this is always the list of elements grouped by id. Since the ids are also
                        // present in the elements themselves, we don't lose any information by discarding the keys.
                        PolymorphicList(map.values.toList())
                    }
                    else -> throw IllegalStateException("FossID returned a type not handled by this deserializer!")
                }
            }

            override fun createContextual(ctxt: DeserializationContext?, property: BeanProperty?): JsonDeserializer<*> {
                // Extract the type from the property, i.e. the T in PolymorphicList.data<T>
                val type = property?.member?.type?.bindings?.getBoundType(0)
                return PolymorphicListDeserializer(type)
            }
        }

        /**
         * Create a FossID service instance for communicating with a server running at the given [url],
         * optionally using a pre-built OkHttp [client].
         */
        fun create(url: String, client: OkHttpClient? = null): FossIdRestService {
            val retrofit = Retrofit.Builder()
                .apply { if (client != null) client(client) }
                .baseUrl(url)
                .addConverterFactory(JacksonConverterFactory.create(JSON_MAPPER))
                .build()

            return retrofit.create(FossIdRestService::class.java)
        }
    }

    @POST("api.php")
    suspend fun getProject(@Body body: PostRequestBody): EntityResponseBody<Project>

    @POST("api.php")
    suspend fun listScansForProject(@Body body: PostRequestBody): PolymorphicResponseBody<Scan>

    @POST("api.php")
    suspend fun createProject(@Body body: PostRequestBody): MapResponseBody<String>

    @POST("api.php")
    suspend fun createScan(@Body body: PostRequestBody): MapResponseBody<String>

    @POST("api.php")
    suspend fun runScan(@Body body: PostRequestBody): EntityResponseBody<Nothing>

    @POST("api.php")
    suspend fun deleteScan(@Body body: PostRequestBody): EntityResponseBody<Int>

    @POST("api.php")
    suspend fun downloadFromGit(@Body body: PostRequestBody): EntityResponseBody<Nothing>

    @POST("api.php")
    suspend fun checkDownloadStatus(@Body body: PostRequestBody): EntityResponseBody<DownloadStatus>

    @POST("api.php")
    suspend fun checkScanStatus(@Body body: PostRequestBody): EntityResponseBody<ScanDescription>

    @POST("api.php")
    suspend fun checkScanStatus2021dot2(@Body body: PostRequestBody): EntityResponseBody<ScanDescription2021dot2>

    @POST("api.php")
    suspend fun listScanResults(@Body body: PostRequestBody): PolymorphicResponseBody<FossIdScanResult>

    @POST("api.php")
    suspend fun listIdentifiedFiles(@Body body: PostRequestBody): PolymorphicResponseBody<IdentifiedFile>

    @POST("api.php")
    suspend fun listMarkedAsIdentifiedFiles(@Body body: PostRequestBody):
            PolymorphicResponseBody<MarkedAsIdentifiedFile>

    @POST("api.php")
    suspend fun listIgnoredFiles(@Body body: PostRequestBody): PolymorphicResponseBody<IgnoredFile>

    @POST("api.php")
    suspend fun listPendingFiles(@Body body: PostRequestBody): PolymorphicResponseBody<String>

    @POST("api.php")
    suspend fun listIgnoreRules(@Body body: PostRequestBody): PolymorphicResponseBody<IgnoreRule>

    @POST("api.php")
    suspend fun createIgnoreRule(@Body body: PostRequestBody): EntityResponseBody<Nothing>

    @POST("api.php")
    suspend fun generateReport(@Body body: PostRequestBody): Response<ResponseBody>

    @GET("index.php?form=login")
    suspend fun getLoginPage(): ResponseBody
}
