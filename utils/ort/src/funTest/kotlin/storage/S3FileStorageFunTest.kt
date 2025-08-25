/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.utils.ort.storage

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.WordSpec
import io.kotest.core.test.TestCase
import io.kotest.engine.test.TestResult
import io.kotest.matchers.shouldBe

import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress

import kotlin.random.Random

import org.apache.logging.log4j.kotlin.logger

class S3FileStorageFunTest : WordSpec() {
    private val loopback = InetAddress.getLoopbackAddress()
    private val protocol = "http"
    private val port = Random.nextInt(1024, 49152) // See https://en.wikipedia.org/wiki/Registered_port.
        .also { logger.debug { "Using port $it for S3 Mock server." } }

    private val bucket = "ort-scan-results"

    private val handler = object : HttpHandler {
        val requests = mutableMapOf<String, String>()

        override fun handle(exchange: HttpExchange) {
            when (exchange.requestMethod) {
                "HEAD" -> {
                    val status = if (requests.containsKey(exchange.requestURI.toString())) {
                        HttpURLConnection.HTTP_OK
                    } else {
                        HttpURLConnection.HTTP_NOT_FOUND
                    }

                    exchange.sendResponseHeaders(status, -1)
                }

                "PUT" -> {
                    val key = exchange.requestURI.toString().removePrefix("/$bucket/")
                    requests[key] = exchange.requestBody.reader().use { it.readText() }.split('\n')[1].trimEnd()
                    exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0)
                }

                "GET" -> {
                    val key = exchange.requestURI.toString().removePrefix("/$bucket/")
                    val data = requests[key]

                    if (data != null) {
                        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0)
                        exchange.responseBody.writer().use { it.write(data) }
                    } else {
                        exchange.sendResponseHeaders(HttpURLConnection.HTTP_NOT_FOUND, 0)
                        exchange.responseBody.bufferedWriter().use {
                            it.write(
                                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                                    "<Error>\n" +
                                    "  <Code>NoSuchKey</Code>\n" +
                                    "  <Message>The resource you requested does not exist</Message>\n" +
                                    "  <Resource>${exchange.requestURI}</Resource> \n" +
                                    "  <RequestId>4442587FB7D0A2F9</RequestId>\n" +
                                    "</Error>"
                            )
                        }
                    }
                }
            }
        }
    }

    // Start a local HTTP server to mock S3 with the system default value for queued incoming connections.
    private val server = HttpServer.create(InetSocketAddress(loopback, port), 0).apply {
        createContext("/", handler)
        start()
    }

    private val storage = S3FileStorage(
        accessKeyId = "key",
        awsRegion = "us-east-1",
        bucketName = bucket,
        compression = false,
        customEndpoint = "$protocol://${loopback.hostAddress}:$port",
        secretAccessKey = "secret"
    )

    override suspend fun afterEach(testCase: TestCase, result: TestResult) {
        handler.requests.clear()
    }

    override suspend fun afterSpec(spec: Spec) {
        // Ensure the server is properly stopped even in case of exceptions, but wait at most 5 seconds.
        server.stop(5)
    }

    init {
        "Querying for a file" should {
            "succeed if the file does not exist" {
                storage.exists("target/file") shouldBe false
            }
        }

        "Writing a file" should {
            "succeed if the file does not exist" {
                shouldNotThrowAny {
                    storage.write("target/file", "content".byteInputStream())
                }

                handler.requests["target/file"] shouldBe "content"
            }
        }

        "Reading a file" should {
            "succeed if the file exists" {
                handler.requests["target/file"] = "content"
                storage.read("target/file").use { input ->
                    val content = input.bufferedReader().readText()
                    content shouldBe "content"
                }
            }

            "fail if the file does not exist" {
                shouldThrow<NoSuchFileException> {
                    storage.read("file-does-not-exist")
                }
            }
        }
    }
}
