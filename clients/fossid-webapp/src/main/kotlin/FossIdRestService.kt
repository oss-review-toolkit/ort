/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import java.util.concurrent.TimeUnit

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.clients.fossid.model.CreateScanResponse
import org.ossreviewtoolkit.clients.fossid.model.Project
import org.ossreviewtoolkit.clients.fossid.model.RemoveUploadContentResponse
import org.ossreviewtoolkit.clients.fossid.model.Scan
import org.ossreviewtoolkit.clients.fossid.model.identification.identifiedFiles.IdentifiedFile
import org.ossreviewtoolkit.clients.fossid.model.identification.ignored.IgnoredFile
import org.ossreviewtoolkit.clients.fossid.model.identification.markedAsIdentified.MarkedAsIdentifiedFile
import org.ossreviewtoolkit.clients.fossid.model.result.FossIdScanResult
import org.ossreviewtoolkit.clients.fossid.model.result.MatchedLines
import org.ossreviewtoolkit.clients.fossid.model.result.Snippet
import org.ossreviewtoolkit.clients.fossid.model.rules.IgnoreRule
import org.ossreviewtoolkit.clients.fossid.model.status.DownloadStatus
import org.ossreviewtoolkit.clients.fossid.model.status.ScanDescription
import org.ossreviewtoolkit.clients.fossid.model.status.ScanDescription2021dot2

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

private const val READ_TIMEOUT_HEADER = "READ_TIMEOUT"

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
                    .addDeserializer(PolymorphicInt::class.java, PolymorphicIntDeserializer())
                    .addDeserializer(PolymorphicData::class.java, PolymorphicDataDeserializer())
            )
        }

        /**
         * A class to modify the standard Jackson deserialization to deal with inconsistencies in responses
         * sent by the FossID server.
         * FossID usually returns data as a List or Map, but in case of no entries it returns a Boolean (which is set to
         * false). This custom deserializer streamlines the result:
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

        /**
         * A custom JSON deserializer implementation to deal with inconsistencies in error responses sent by FossID
         * for requests returning a single value. If such a request fails, the response from FossID contains an
         * empty array for the value, which cannot be handled by the default deserialization.
         */
        private class PolymorphicDataDeserializer(val boundType: JavaType? = null) :
            StdDeserializer<PolymorphicData<Any>>(PolymorphicData::class.java), ContextualDeserializer {
            override fun deserialize(p: JsonParser, ctxt: DeserializationContext): PolymorphicData<Any> {
                requireNotNull(boundType) {
                    "The PolymorphicDataDeserializer needs a type to deserialize values!"
                }

                return when (p.currentToken) {
                    JsonToken.START_ARRAY -> {
                        val arrayType = JSON_MAPPER.typeFactory.constructArrayType(boundType)
                        val array = JSON_MAPPER.readValue<Array<Any>>(p, arrayType)
                        PolymorphicData(array.firstOrNull())
                    }

                    JsonToken.START_OBJECT -> {
                        val data = JSON_MAPPER.readValue<Any>(p, boundType)
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
        private class PolymorphicIntDeserializer :
            StdDeserializer<PolymorphicInt>(PolymorphicInt::class.java) {
            override fun deserialize(p: JsonParser, ctxt: DeserializationContext): PolymorphicInt {
                return when (p.currentToken) {
                    JsonToken.VALUE_STRING -> {
                        val value = JSON_MAPPER.readValue(p, String::class.java)
                        PolymorphicInt(value.toInt())
                    }

                    JsonToken.VALUE_NUMBER_INT -> {
                        val value = JSON_MAPPER.readValue(p, Int::class.java)
                        PolymorphicInt(value)
                    }

                    JsonToken.START_ARRAY -> {
                        val array = JSON_MAPPER.readValue(p, IntArray::class.java)
                        val value = if (array.isEmpty()) null else array.first()
                        PolymorphicInt(value)
                    }

                    JsonToken.START_OBJECT -> {
                        val mapType = JSON_MAPPER.typeFactory.constructMapType(
                            LinkedHashMap::class.java,
                            String::class.java,
                            String::class.java
                        )
                        val map = JSON_MAPPER.readValue<Map<Any, Any>>(p, mapType)
                        if (map.size != 1) {
                            error("A map representing a polymorphic integer should have one value!")
                        }

                        PolymorphicInt(map.values.first().toString().toInt())
                    }

                    else -> error("FossID returned a type not handled by this deserializer!")
                }
            }
        }

        /**
         * Create the [FossIdServiceWithVersion] to interact with the FossID instance running at the given [url],
         * optionally using a pre-built OkHttp [client].
         */
        suspend fun create(
            url: String,
            client: OkHttpClient? = null,
            logRequests: Boolean = false
        ): FossIdServiceWithVersion {
            logger.info { "The FossID server URL is $url." }

            val builder = client?.newBuilder() ?: OkHttpClient.Builder()
            if (logRequests) {
                builder.addInterceptor(LoggingInterceptor)
            }

            val retrofit = Retrofit.Builder()
                .client(builder.addInterceptor(TimeoutInterceptor).build())
                .baseUrl(url)
                .addConverterFactory(JacksonConverterFactory.create(JSON_MAPPER))
                .build()

            val service = retrofit.create(FossIdRestService::class.java)

            return FossIdServiceWithVersion.create(service).also {
                if (it.version.isEmpty()) {
                    logger.warn { "The FossID server is running an unknown version." }
                } else {
                    logger.info { "The FossID server is running version ${it.version}." }
                }
            }
        }
    }

    /**
     * An interceptor to set the timeout of the requests based on the call headers. If no timeout header is present,
     * default timeout of the client is used.
     */
    object TimeoutInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
            val request = chain.request()
            val newReadTimeout = request.header(READ_TIMEOUT_HEADER)

            val readTimeout = newReadTimeout?.toIntOrNull() ?: chain.readTimeoutMillis()

            return chain.withReadTimeout(readTimeout, TimeUnit.MILLISECONDS).proceed(request)
        }
    }

    @POST("api.php")
    suspend fun getProject(@Body body: PostRequestBody): PolymorphicDataResponseBody<Project>

    @POST("api.php")
    suspend fun listProjects(@Body body: PostRequestBody): PolymorphicResponseBody<Project>

    @POST("api.php")
    suspend fun getScan(@Body body: PostRequestBody): PolymorphicDataResponseBody<Scan>

    @POST("api.php")
    suspend fun listScansForProject(@Body body: PostRequestBody): PolymorphicResponseBody<Scan>

    @POST("api.php")
    suspend fun createProject(@Body body: PostRequestBody): MapResponseBody<String>

    @POST("api.php")
    suspend fun createScan(@Body body: PostRequestBody): PolymorphicDataResponseBody<CreateScanResponse>

    @POST("api.php")
    suspend fun runScan(@Body body: PostRequestBody): EntityResponseBody<Nothing>

    @POST("api.php")
    @Headers("$READ_TIMEOUT_HEADER:${5 * 60 * 1000}")
    suspend fun deleteScan(@Body body: PostRequestBody): EntityResponseBody<PolymorphicInt>

    @POST("api.php")
    suspend fun downloadFromGit(@Body body: PostRequestBody): EntityResponseBody<Nothing>

    @POST("api.php")
    suspend fun checkDownloadStatus(@Body body: PostRequestBody): PolymorphicDataResponseBody<DownloadStatus>

    @POST("api.php")
    suspend fun checkScanStatus(@Body body: PostRequestBody): EntityResponseBody<ScanDescription>

    @POST("api.php")
    suspend fun checkScanStatus2021dot2(@Body body: PostRequestBody): EntityResponseBody<ScanDescription2021dot2>

    @POST("api.php")
    suspend fun listScanResults(@Body body: PostRequestBody): PolymorphicResponseBody<FossIdScanResult>

    @POST("api.php")
    suspend fun listSnippets(@Body body: PostRequestBody): PolymorphicResponseBody<Snippet>

    @POST("api.php")
    @Headers("$READ_TIMEOUT_HEADER:${5 * 60 * 1000}")
    suspend fun listMatchedLines(@Body body: PostRequestBody): PolymorphicDataResponseBody<MatchedLines>

    @POST("api.php")
    @Headers("$READ_TIMEOUT_HEADER:${60 * 1000}")
    suspend fun listIdentifiedFiles(@Body body: PostRequestBody): PolymorphicResponseBody<IdentifiedFile>

    @POST("api.php")
    suspend fun listMarkedAsIdentifiedFiles(
        @Body body: PostRequestBody
    ): PolymorphicResponseBody<MarkedAsIdentifiedFile>

    @POST("api.php")
    suspend fun listIgnoredFiles(@Body body: PostRequestBody): PolymorphicResponseBody<IgnoredFile>

    @POST("api.php")
    suspend fun listPendingFiles(@Body body: PostRequestBody): PolymorphicResponseBody<String>

    @POST("api.php")
    suspend fun listIgnoreRules(@Body body: PostRequestBody): PolymorphicResponseBody<IgnoreRule>

    @POST("api.php")
    suspend fun createIgnoreRule(@Body body: PostRequestBody): EntityResponseBody<Nothing>

    @POST("api.php")
    @Headers("$READ_TIMEOUT_HEADER:${5 * 60 * 1000}")
    suspend fun generateReport(@Body body: PostRequestBody): Response<ResponseBody>

    @POST("api.php")
    suspend fun markAsIdentified(@Body body: PostRequestBody): EntityResponseBody<Nothing>

    @POST("api.php")
    suspend fun unmarkAsIdentified(@Body body: PostRequestBody): EntityResponseBody<Nothing>

    @POST("api.php")
    suspend fun addLicenseIdentification(@Body body: PostRequestBody): EntityResponseBody<Nothing>

    @POST("api.php")
    suspend fun addComponentIdentification(@Body body: PostRequestBody): EntityResponseBody<Nothing>

    @POST("api.php")
    suspend fun addFileComment(@Body body: PostRequestBody): EntityResponseBody<Nothing>

    @POST("api.php")
    suspend fun extractArchives(@Body body: PostRequestBody): EntityResponseBody<Boolean>

    @POST("api.php")
    suspend fun removeUploadedContent(
        @Body body: PostRequestBody
    ): PolymorphicDataResponseBody<RemoveUploadContentResponse>

    @POST("api.php")
    @Headers("Content-Type: application/octet-stream")
    suspend fun uploadFile(
        @Header("FOSSID-SCAN-CODE") scanCodeB64: String,
        @Header("FOSSID-FILE-NAME") fileNameB64: String,
        @Header("Authorization") authorization: String,
        @Header("Transfer-Encoding") transferEncoding: String,
        @Body payload: RequestBody
    ): EntityResponseBody<Nothing>

    @GET("index.php?form=login")
    suspend fun getLoginPage(): ResponseBody
}
