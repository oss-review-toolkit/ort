/*
 * Copyright (C) 2025 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode

import java.nio.charset.StandardCharsets

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.internal.http.promisesBody

import okio.Buffer
import okio.GzipSource

import org.apache.logging.log4j.kotlin.logger

/**
 * An interceptor that logs the body of FossID requests, but takes care that the credentials are not logged.
 * This function implementation is partly taken from the OkHttp Logging Interceptor at
 * https://github.com/square/okhttp/blob/parent-5.0.0/okhttp-logging-interceptor/src/main/kotlin/okhttp3/logging/HttpLoggingInterceptor.kt
 * which cannot be used directly as it does not support modifying the request body before logging it.
 */
object LoggingInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestBody = request.body
        val connection = chain.connection()

        val requestLogMessage = buildString {
            val protocol = if (connection != null) " ${connection.protocol()}" else ""
            val requestStartMessage = "--> ${request.method} ${request.url}$protocol"
            append(requestStartMessage)

            if (requestBody != null) {
                append(" (${requestBody.contentLength()}-byte body)")

                val buffer = Buffer()
                requestBody.writeTo(buffer)

                val contentType = requestBody.contentType()
                val charset = contentType?.charset(StandardCharsets.UTF_8) ?: StandardCharsets.UTF_8

                val requestContentType = listOfNotNull(request.header("Content-Type"), contentType?.toString())
                if ("application/octet-stream" in requestContentType) {
                    append(
                        "--> END ${request.method} (binary ${requestBody.contentLength()}-byte body omitted)"
                    )
                } else {
                    val requestContent = buffer.readString(charset)
                    append(requestContent.sanitizeForLogging())
                    append("--> END ${request.method} (${requestBody.contentLength()}-byte body)")
                }
            }
        }

        logger.info { requestLogMessage }

        val response = runCatching {
            chain.proceed(request)
        }.onFailure {
            logger.info(it) { "<-- HTTP FAILED" }
        }.getOrThrow()

        val contentLength = response.body.contentLength()
        val bodySize = if (contentLength != -1L) "$contentLength-byte" else "unknown-length"
        val responseMessage = if (response.message.isEmpty()) "" else " ${response.message}"

        val responseLogMessage = buildString {
            append("<-- ${response.code}$responseMessage ${response.request.url}, $bodySize body\"")

            val source = response.body.source()
            source.request(Long.MAX_VALUE) // Buffer the entire body.
            var buffer = source.buffer

            var gzippedLength: Long? = null
            if ("gzip".equals(request.header("Content-Encoding"), ignoreCase = true)) {
                gzippedLength = buffer.size
                GzipSource(buffer.clone()).use { gzippedResponseBody ->
                    buffer = Buffer()
                    buffer.writeAll(gzippedResponseBody)
                }
            }

            val contentType = response.body.contentType()
            val charset = contentType?.charset(StandardCharsets.UTF_8) ?: StandardCharsets.UTF_8

            if (!response.promisesBody()) {
                append("<-- END HTTP")
            } else if (response.header("Content-Type") == "application/octet-stream") {
                append("<-- END HTTP (binary ${buffer.size}-byte body omitted)")
            } else {
                append(buffer.clone().readString(charset))

                if (gzippedLength != null) {
                    append("<-- END HTTP (${buffer.size}-byte, $gzippedLength-gzipped-byte body)")
                } else {
                    append("<-- END HTTP (${buffer.size}-byte body)")
                }
            }
        }

        logger.info { responseLogMessage }

        return response
    }
}

/**
 * Sanitize the string for logging purposes by removing sensitive information like credentials.
 */
private fun String.sanitizeForLogging(): String {
    val requestJson = FossIdRestService.JSON_MAPPER.readTree(this)
    val dataObject = requestJson.get("data")

    if (dataObject is ObjectNode) {
        dataObject.remove("username")
        dataObject.remove("key")
        val urlWithCredentials = dataObject.get("git_repo_url")

        // Replace the credentials in the Git repository URL. The function [replaceCredentialsInUri]
        // from utils.common is not used to avoid a dependency in the FossID client.
        if (urlWithCredentials != null && urlWithCredentials is TextNode) {
            val url = urlWithCredentials.toString().replace("//[\\w:]+@".toRegex(), "//")
            dataObject.set<ObjectNode>("git_repo_url", TextNode(url))
        }
    }

    return requestJson.toString()
}
