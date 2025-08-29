/*
 * Copyright (C) 2017 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.utils.ort

import java.io.File
import java.io.IOException
import java.lang.invoke.MethodHandles
import java.net.HttpURLConnection
import java.net.URI
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

import kotlinx.coroutines.suspendCancellableCoroutine

import okhttp3.Authenticator
import okhttp3.Cache
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionSpec
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.Route

import okio.buffer
import okio.sink

import org.apache.logging.log4j.kotlin.logger
import org.apache.logging.log4j.kotlin.loggerOf

import org.ossreviewtoolkit.utils.common.ArchiveType
import org.ossreviewtoolkit.utils.common.collectMessages
import org.ossreviewtoolkit.utils.common.div
import org.ossreviewtoolkit.utils.common.gibibytes
import org.ossreviewtoolkit.utils.common.unquote
import org.ossreviewtoolkit.utils.common.withoutPrefix

typealias BuilderConfiguration = OkHttpClient.Builder.() -> Unit

/**
 * A helper class to manage OkHttp instances backed by distinct cache directories.
 */
object OkHttpClientHelper {
    /**
     * A constant for the "too many requests" HTTP code as HttpURLConnection has none.
     */
    const val HTTP_TOO_MANY_REQUESTS = 429

    private val clients = ConcurrentHashMap<BuilderConfiguration, OkHttpClient>()

    /**
     * Build a preconfigured client that uses a cache directory inside the [ORT data directory][ortDataDirectory].
     * Proxy environment variables are by default respected, but the client can further be configured via the [block].
     */
    fun buildClient(block: BuilderConfiguration? = null): OkHttpClient =
        block?.let {
            clients.getOrPut(it) { okHttpClient.newBuilder().apply(block).build() }
        } ?: okHttpClient
}

/**
 * An HTTP-specific download error that enriches an [IOException] with an additional HTTP error code.
 */
class HttpDownloadError(val code: Int, message: String) : IOException("$message (HTTP code $code)")

private val logger = loggerOf(MethodHandles.lookup().lookupClass())

private const val CACHE_DIRECTORY = "cache/http"
private val MAX_CACHE_SIZE_IN_BYTES = 1.gibibytes
private const val READ_TIMEOUT_IN_SECONDS = 30L
private const val AUTHORIZATION_HEADER = "Authorization"

/**
 * The default [OkHttpClient] for ORT to use.
 */
val okHttpClient: OkHttpClient by lazy {
    OrtAuthenticator.install()
    OrtProxySelector.install()

    val cacheDirectory = ortDataDirectory / CACHE_DIRECTORY
    val cache = Cache(cacheDirectory, MAX_CACHE_SIZE_IN_BYTES)
    val specs = listOf(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT)

    // OkHttp uses Java's global ProxySelector by default, but the Authenticator for a proxy needs to be set
    // explicitly. Also note that the (non-proxy) authenticator is set here and is primarily intended for "reactive"
    // authentication, but most often "preemptive" authentication via headers is required. For proxy authentication,
    // OkHttp emulates preemptive authentication by sending a fake "OkHttp-Preemptive" response to the reactive
    // proxy authenticator.
    OkHttpClient.Builder()
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            val requestWithUserAgent = request.takeUnless { it.header("User-Agent") == null }
                ?: request.newBuilder().header("User-Agent", ORT_USER_AGENT).build()

            runCatching {
                chain.proceed(requestWithUserAgent)
            }.onFailure {
                it.showStackTrace()

                logger.error {
                    "HTTP request to ${request.url} failed with an exception: ${it.collectMessages()}"
                }
            }.getOrThrow()
        }
        .cache(cache)
        .connectionSpecs(specs)
        .readTimeout(Duration.ofSeconds(READ_TIMEOUT_IN_SECONDS))
        .authenticator(JavaNetAuthenticatorWrapper())
        .proxyAuthenticator(Authenticator.JAVA_NET_AUTHENTICATOR)
        .build()
}

/**
 * Add a request interceptor that injects basic authorization using the [username] and [password] into the client
 * builder.
 */
fun OkHttpClient.Builder.addBasicAuthorization(username: String, password: String): OkHttpClient.Builder =
    addInterceptor { chain ->
        val requestBuilder = chain.request().newBuilder()
            .header("Authorization", Credentials.basic(username, password))

        chain.proceed(requestBuilder.build())
    }

/**
 * Download from [url] and return a [Result] with a file inside [directory] that holds the response body content on
 * success, or a [Result] wrapping an [IOException] (which might be a [HttpDownloadError]) on failure.
 */
fun OkHttpClient.downloadFile(url: String, directory: File): Result<File> {
    if (url.startsWith("file:/")) {
        val source = File(URI(url))
        val target = directory / source.name
        return runCatching { source.copyTo(target) }
    }

    // Disable transparent gzip compression, as otherwise we might end up writing a tar file to disk while
    // expecting to find a tar.gz file, and fail to unpack the archive. See
    // https://github.com/square/okhttp/blob/parent-3.10.0/okhttp/src/main/java/okhttp3/internal/http/BridgeInterceptor.java#L79
    return download(url, acceptEncoding = "identity").mapCatching { (response, body) ->
        // Depending on the server, we may only get a useful target file name when looking at the response
        // header or at a redirected URL. In case of the Crates registry, for example, we want to resolve
        //     https://crates.io/api/v1/crates/cfg-if/0.1.9/download
        // to
        //     https://static.crates.io/crates/cfg-if/cfg-if-0.1.9.crate
        //
        // On the other hand, e.g. for GitHub exactly the opposite is the case, as there a get-request for URL
        //     https://github.com/microsoft/tslib/archive/1.10.0.zip
        // resolves to the less meaningful
        //     https://codeload.github.com/microsoft/tslib/zip/1.10.0
        //
        // So first look for a dedicated header in the response, but then also try both redirected and
        // original URLs to find a name which has a recognized archive type extension.
        val candidateNames = mutableSetOf<String>()

        response.headers("Content-disposition").mapNotNullTo(candidateNames) { value ->
            value.split(';').firstNotNullOfOrNull { it.trim().withoutPrefix("filename=") }?.unquote()
        }

        listOf(response.request.url.toString(), url).mapTo(candidateNames) {
            it.substringAfterLast('/').substringBefore('?')
        }

        check(candidateNames.isNotEmpty())

        val filename = candidateNames.find {
            ArchiveType.getType(it) != ArchiveType.NONE
        } ?: candidateNames.first()

        val file = directory / filename

        file.sink().buffer().use { target ->
            body.use { target.writeAll(it.source()) }
        }

        file
    }
}

/**
 * Download from [url] and return a [Result] with a string representing the response body content on success, or a
 * [Result] wrapping an [IOException] (which might be a [HttpDownloadError]) on failure.
 */
fun OkHttpClient.downloadText(url: String): Result<String> {
    if (url.startsWith("file:/")) return runCatching { File(URI(url)).readText() }

    return download(url).mapCatching { (_, body) ->
        body.use { it.string() }
    }
}

/**
 * Download from [url] with optional [acceptEncoding] and return a [Result] with the [Response] and non-nullable
 * [ResponseBody] on success, or a [Result] wrapping an [IOException] (which might be a [HttpDownloadError]) on
 * failure.
 */
fun OkHttpClient.download(url: String, acceptEncoding: String? = null): Result<Pair<Response, ResponseBody>> =
    runCatching {
        val request = Request.Builder()
            .apply { acceptEncoding?.also { header("Accept-Encoding", it) } }
            .get()
            .url(url)
            .build()

        execute(request)
    }.mapCatching { response ->
        if (!response.isSuccessful) {
            response.body.close()
            throw HttpDownloadError(response.code, response.message)
        }

        response to response.body
    }

/**
 * Execute a HEAD-request against [url] to ping for its existence.
 */
fun OkHttpClient.ping(url: String): Response =
    Request.Builder().head().url(url).build().let { request ->
        execute(request)
    }

/**
 * Execute a [request] using the client.
 */
fun OkHttpClient.execute(request: Request): Response =
    newCall(request).execute().also { response ->
        logger.debug {
            if (response.cacheResponse != null) {
                "Retrieved ${response.request.url} from local cache."
            } else {
                "Downloaded from ${response.request.url} via network."
            }
        }
    }

/**
 * Asynchronously enqueue a [request] using the client and await its response.
 */
suspend fun OkHttpClient.await(request: Request): Response = newCall(request).await()

/**
 * Asynchronously enqueue the [Call]'s request and await its [Response].
 */
suspend fun Call.await(): Response =
    suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation {
            cancel()
        }

        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }

            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }
        })
    }

/**
 * A wrapper implementation around OkHttp's [Authenticator.JAVA_NET_AUTHENTICATOR] that is less strict about querying
 * credentials for requests from the global Java authenticator.
 *
 * OkHttp already has a built-in [Authenticator] that uses the global Java authenticator; however, this implementation
 * is rather picky about the requests for which it delegates to the Java authenticator. It only kicks in for responses
 * containing a challenge with the "Basic" scheme. This excludes a number of servers that could be handled by the Java
 * authenticator. For instance, the GitHub package registry does not send any challenges, but it can be accessed with a
 * token provided by the Java authenticator.
 *
 * This implementation handles default authentication request by always querying the Java authenticator for credentials,
 * no matter which challenges are sent by the server. Only in special cases, such as unexpected response codes, it
 * delegates to the default OkHttp authenticator.
 */
internal class JavaNetAuthenticatorWrapper(
    private val wrappedAuthenticator: Authenticator = Authenticator.JAVA_NET_AUTHENTICATOR
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.code != HttpURLConnection.HTTP_UNAUTHORIZED) {
            return wrappedAuthenticator.authenticate(route, response)
        }

        // The request already had an Authorization header; so obviously the credentials are not valid.
        if (response.request.header(AUTHORIZATION_HEADER) != null) return null

        val requestUri = response.request.url.toUri()
        return requestPasswordAuthentication(requestUri)?.let { authentication ->
            response.request.newBuilder()
                .header(
                    AUTHORIZATION_HEADER,
                    Credentials.basic(
                        authentication.userName,
                        String(authentication.password)
                    )
                ).build()
        }
    }
}
