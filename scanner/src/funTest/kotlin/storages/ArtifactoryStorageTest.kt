/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
 * Copyright (C) 2019 Bosch Software Innovations GmbH
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

package com.here.ort.scanner.storages

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer

import io.kotlintest.Spec
import io.kotlintest.TestCase

import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress

import kotlin.random.Random

class ArtifactoryStorageTest : AbstractStorageTest() {
    private val loopback = InetAddress.getLoopbackAddress()
    private val port = Random.nextInt(1024, 49152) // See https://en.wikipedia.org/wiki/Registered_port.

    private val handler = object : HttpHandler {
        val requests = mutableMapOf<String, String>()

        override fun handle(exchange: HttpExchange) {
            when (exchange.requestMethod) {
                "PUT" -> {
                    exchange.sendResponseHeaders(HttpURLConnection.HTTP_CREATED, 0)
                    requests[exchange.requestURI.toString()] = exchange.requestBody.reader().use { it.readText() }
                }
                "GET" -> {
                    exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0)
                    exchange.responseBody.writer().use { it.write(requests[exchange.requestURI.toString()]) }
                }
            }
        }
    }

    // Start the local HTTP server with the system default value for queued incoming connections.
    private val server = HttpServer.create(InetSocketAddress(loopback, port), 0).apply {
        createContext("/", handler)
        start()
    }

    override fun beforeTest(testCase: TestCase) {
        handler.requests.clear()

        super.beforeTest(testCase)
    }

    override fun afterSpec(spec: Spec) {
        // Ensure the server is properly stopped even in case of exceptions, but wait at most 5 seconds.
        server.stop(5)

        super.afterSpec(spec)
    }

    override fun createStorage() = ArtifactoryStorage("http://${loopback.hostAddress}:$port", "repository", "apiToken")
}
