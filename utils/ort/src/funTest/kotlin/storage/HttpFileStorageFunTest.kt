/*
 * Copyright (C) 2019 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.WordSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.matchers.shouldBe

import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress

import kotlin.random.Random

import org.ossreviewtoolkit.utils.ort.log

class HttpFileStorageFunTest : WordSpec() {
    private val loopback = InetAddress.getLoopbackAddress()
    private val port = Random.nextInt(1024, 49152) // See https://en.wikipedia.org/wiki/Registered_port.
        .also { log.debug { "Using port $it for HTTP server." } }

    private val handler = object : HttpHandler {
        val requests = mutableMapOf<String, String>()

        override fun handle(exchange: HttpExchange) {
            when (exchange.requestMethod) {
                "PUT" -> {
                    exchange.sendResponseHeaders(HttpURLConnection.HTTP_CREATED, 0)
                    requests[exchange.requestURI.toString()] = exchange.requestBody.reader().use { it.readText() }
                }
                "GET" -> {
                    requests[exchange.requestURI.toString()].let { data ->
                        if (data != null) {
                            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0)
                            exchange.responseBody.writer().use { it.write(data) }
                        } else {
                            exchange.sendResponseHeaders(HttpURLConnection.HTTP_NOT_FOUND, 0)
                        }
                    }
                }
            }
        }
    }

    // Start the local HTTP server with the system default value for queued incoming connections.
    private val server = HttpServer.create(InetSocketAddress(loopback, port), 0).apply {
        createContext("/", handler)
        start()
    }

    private val storage = HttpFileStorage("http://${loopback.hostAddress}:$port")

    override suspend fun afterEach(testCase: TestCase, result: TestResult) {
        handler.requests.clear()
    }

    override suspend fun afterSpec(spec: Spec) {
        // Ensure the server is properly stopped even in case of exceptions, but wait at most 5 seconds.
        server.stop(5)
    }

    init {
        "Reading a file" should {
            "succeed if the file exists" {
                handler.requests["/file-exists"] = "file-exists"

                storage.read("file-exists").use { input ->
                    val content = input.bufferedReader().readText()

                    content shouldBe "file-exists"
                }
            }

            "fail if the file does not exist" {
                shouldThrow<IOException> {
                    storage.read("file-does-not-exist")
                }
            }
        }

        "Writing a file" should {
            "succeed if the file does not exist" {
                storage.write("target/file", "content".byteInputStream())

                handler.requests["/target/file"] shouldBe "content"
            }

            "succeed if the file does exist" {
                handler.requests["/target/file"] = "old content"

                storage.write("target/file", "content".byteInputStream())

                handler.requests["/target/file"] shouldBe "content"
            }
        }
    }
}
