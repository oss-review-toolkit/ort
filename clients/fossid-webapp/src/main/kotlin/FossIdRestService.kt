/*
 * Copyright (C) 2020 Bosch.IO GmbH
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

@file:Suppress("TooManyFunctions")

package org.ossreviewtoolkit.clients.fossid

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.BeanDescription
import com.fasterxml.jackson.databind.DeserializationConfig
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier
import com.fasterxml.jackson.databind.deser.std.DelegatingDeserializer
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.type.CollectionType
import com.fasterxml.jackson.databind.type.MapType
import com.fasterxml.jackson.module.kotlin.kotlinModule

import okhttp3.OkHttpClient
import okhttp3.ResponseBody

import org.ossreviewtoolkit.clients.fossid.model.Project
import org.ossreviewtoolkit.clients.fossid.model.Scan
import org.ossreviewtoolkit.clients.fossid.model.identification.identifiedFiles.IdentifiedFile
import org.ossreviewtoolkit.clients.fossid.model.identification.ignored.IgnoredFile
import org.ossreviewtoolkit.clients.fossid.model.identification.markedAsIdentified.MarkedAsIdentifiedFile
import org.ossreviewtoolkit.clients.fossid.model.result.FossIdScanResult
import org.ossreviewtoolkit.clients.fossid.model.status.DownloadStatus
import org.ossreviewtoolkit.clients.fossid.model.status.ScanStatus

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
        val JSON_MAPPER: ObjectMapper = JsonMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            // FossID has a bug in get_results/scan.
            // Sometimes the match_type is "ignored", sometimes it is "Ignored".
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .registerModule(kotlinModule().setDeserializerModifier(FossIdDeserializerModifier()))

        /**
         * A class to modify the standard Jackson deserialization to deal with inconsistencies in responses
         * sent by the FossID server.
         * FossID usually returns data as a List or Map, but in case of no entries it returns a Boolean (which is set to
         * false). To handle this special case, this class makes sure that a custom deserializer is used for lists or
         * maps that detects such a result and converts it to an empty list or map, respective. That way, the FossID
         * service interface can use meaningful result types.
         */
        private class FossIdDeserializerModifier : BeanDeserializerModifier() {
            override fun modifyMapDeserializer(
                config: DeserializationConfig,
                type: MapType,
                beanDesc: BeanDescription,
                deserializer: JsonDeserializer<*>
            ): JsonDeserializer<*> = FalseValueDeserializer(deserializer) { mutableMapOf<Any, Any>() }

            override fun modifyCollectionDeserializer(
                config: DeserializationConfig?,
                type: CollectionType?,
                beanDesc: BeanDescription?,
                deserializer: JsonDeserializer<*>
            ): JsonDeserializer<*> = FalseValueDeserializer(deserializer) { mutableListOf<Any>() }
        }

        /**
         * Custom Jackson deserializer that abstracts the behaviour explained above. [creator] is a function that
         * creates the 'replacement' object when a Boolean value set to false is received. When not, the standard
         * Kotlin/Jackson deserializer is called by delegation.
         */
        private class FalseValueDeserializer(delegate: JsonDeserializer<*>, private val creator: () -> Any) :
            DelegatingDeserializer(delegate) {
            override fun deserialize(p: JsonParser, ctxt: DeserializationContext): Any =
                if (p.currentToken == JsonToken.VALUE_FALSE) creator() else super.deserialize(p, ctxt)

            override fun newDelegatingInstance(newDelegatee: JsonDeserializer<*>): JsonDeserializer<*> =
                FalseValueDeserializer(newDelegatee, creator)
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
    suspend fun listScansForProject(@Body body: PostRequestBody): MapResponseBody<Scan>

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
    suspend fun checkScanStatus(@Body body: PostRequestBody): EntityResponseBody<ScanStatus>

    @POST("api.php")
    suspend fun listScanResults(@Body body: PostRequestBody): MapResponseBody<FossIdScanResult>

    @POST("api.php")
    suspend fun listIdentifiedFiles(@Body body: PostRequestBody): MapResponseBody<IdentifiedFile>

    @POST("api.php")
    suspend fun listMarkedAsIdentifiedFiles(@Body body: PostRequestBody): MapResponseBody<MarkedAsIdentifiedFile>

    @POST("api.php")
    suspend fun listIgnoredFiles(@Body body: PostRequestBody): EntityResponseBody<List<IgnoredFile>>

    @POST("api.php")
    suspend fun listPendingFiles(@Body body: PostRequestBody): MapResponseBody<String>

    @GET("index.php?form=login")
    suspend fun getLoginPage(): ResponseBody
}
