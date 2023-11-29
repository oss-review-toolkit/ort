/*
 * SPDX-FileCopyrightText: 2023 HH Partners
 *
 * SPDX-License-Identifier: MIT
 */

package org.ossreviewtoolkit.clients.dos

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory

import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.Interceptor

import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody

import org.apache.logging.log4j.kotlin.Logging
import retrofit2.Invocation

import retrofit2.http.*
import retrofit2.Response
import retrofit2.Retrofit
import java.time.Duration

/**
 * A Retrofit2 interface to define the network layer of the DOS client.
 */
interface DOSService {

    companion object: Logging {
        /**
         * The JSON (de-)serialization object used by this service.
         */
        private val JSON = Json {
            ignoreUnknownKeys = true
        }

        /**
         * Create a new service instance that connects to the [url] specified and uses the optionally provided [client].
         */
        fun create(url: String, token: String, timeout: Int, client: OkHttpClient? = null): DOSService {
            val contentType = "application/json; charset=utf-8".toMediaType()

            val loggingInterceptor = HttpLoggingInterceptor().apply {
                // For logging basic call-response statuses, use BASIC
                // For logging the request and response bodies of a call, use BODY
                level = HttpLoggingInterceptor.Level.NONE
            }

            val authInterceptor = AuthInterceptor(token)

            val okHttpClient = client ?: OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(timeout.toLong()))
                .connectTimeout(Duration.ofSeconds(timeout.toLong()))
                .readTimeout(Duration.ofSeconds(timeout.toLong()))
                .writeTimeout(Duration.ofSeconds(timeout.toLong()))
                .addInterceptor(loggingInterceptor)
                .addInterceptor(authInterceptor)
                .build()

            val retrofit = Retrofit.Builder()
                .client(okHttpClient)
                .baseUrl(url)
                .addConverterFactory(JSON.asConverterFactory(contentType))
                .build()

            return retrofit.create(DOSService::class.java)
        }
    }

    @Serializable
    data class UploadUrlRequestBody(
        val key: String? = null
    )

    @Serializable
    data class UploadUrlResponseBody(
        val success: Boolean,
        val presignedUrl: String? = null,
        val message: String? = null
    )

    @Serializable
    data class ScanResultsRequestBody(
        val purl: String? = null,
        val options: ReqOptions? = null
    ) {
        @Serializable
        data class ReqOptions(
            val fetchConcluded: Boolean? = false
        )
    }

    @Serializable
    data class ScanResultsResponseBody(
        var state: State,
        val results: JsonElement? = null
    ) {
        @Serializable
        data class State(
            var status: String,
            val id: String? = null
        )
    }

    // When backend is ready, change the purl to a list of purls
    @Serializable
    data class JobRequestBody(
        val zipFileKey: String? = null,
        val purl: String? = null
    )

    @Serializable
    data class JobResponseBody(
        val scannerJobId: String? = null,
        val message: String? = null
    )

    @Serializable
    data class JobStateResponseBody(
        var state: State,
        val results: JsonElement? = null
    ) {
        @Serializable
        data class State(
            var status: String? = null,
            val message: String? = null
        )
    }

    @Serializable
    data class PackageConfigurationRequestBody(
        val purl: String? = null
    )

    @Serializable
    data class PackageConfigurationResponseBody(
        val licenseConclusions: List<LicenseConclusion>,
        val pathExclusions: List<PathExclusion>
    ) {
        @Serializable
        data class LicenseConclusion(
            val path: String,
            val detectedLicenseExpressionSPDX: String? = null,
            val concludedLicenseExpressionSPDX: String,
            val comment: String? = null
        )

        @Serializable
        data class PathExclusion(
            val pattern: String,
            val reason: String,
            val comment: String? = null
        )
    }

    /**
     * Custom annotation for skipping the Authorization Interceptor for certain network calls
     */
    @Target(AnnotationTarget.FUNCTION)
    @Retention(AnnotationRetention.RUNTIME)
    annotation class SkipAuthentication

    /**
     * S3 Object Storage doesn't accept authorization headers for the presigned URL
     * PUT method, so skip the authorization for this function call
     */
    @SkipAuthentication
    @PUT
    suspend fun putS3File(@Url url: String, @Body file: RequestBody): Response<Unit>

    @POST("upload-url")
    suspend fun postUploadUrl(@Body body: UploadUrlRequestBody): Response<UploadUrlResponseBody>

    @POST("scan-results")
    suspend fun postScanResults(@Body body: ScanResultsRequestBody): Response<ScanResultsResponseBody>

    @POST("job")
    suspend fun postJob(@Body body: JobRequestBody): Response<JobResponseBody>

    @GET("job-state/{id}")
    suspend fun getJobState(@Path("id") id: String): Response<JobStateResponseBody>

    @POST("package-configuration")
    suspend fun postPackageConfiguration(@Body body: PackageConfigurationRequestBody): Response<PackageConfigurationResponseBody>

    /**
     * Authorization Interceptor
     */
    class AuthInterceptor(private val token: String): Interceptor {
        override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
            val original = chain.request()

            // Check for the SkipAuthentication annotation
            val skipAuthentication =
                original.tag(Invocation::class.java)?.
                    method()?.
                    isAnnotationPresent(SkipAuthentication::class.java) == true

            if (skipAuthentication) {
                return chain.proceed(original)
            }

            val requestBuilder = original.newBuilder()
                .header("Authorization", "Bearer $token")
                .method(original.method, original.body)
            return chain.proceed(requestBuilder.build())
        }
    }
}
