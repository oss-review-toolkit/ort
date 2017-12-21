/*
 * Copyright (c) 2017 HERE Europe B.V.
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

package com.here.ort.scanner

import com.here.ort.model.Package
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.VcsInfo

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldEqual
import io.kotlintest.specs.StringSpec
import io.kotlintest.TestCaseContext

import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress

class HttpCacheTest : StringSpec() {
    private val loopback = InetAddress.getLoopbackAddress()
    private val port = 8888

    private class MyHttpHandler : HttpHandler {
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

    override fun interceptTestCase(context: TestCaseContext, test: () -> Unit) {
        val server = HttpServer.create(InetSocketAddress(loopback, port), 0)

        try {
            // Start the local HTTP server.
            server.createContext("/", MyHttpHandler())
            server.start()

            test()
        } finally {
            // Ensure the server is properly stopped even in case of exceptions.
            server.stop(0)
        }
    }

    init {
        "HTTP GET returns what was PUT" {
            val cache = ArtifactoryCache("http://${loopback.hostAddress}:$port", "apiToken")

            val pkg = Package(
                    packageManager = "packageManager",
                    namespace = "namespace",
                    name = "name",
                    version = "version",
                    declaredLicenses = sortedSetOf("license"),
                    description = "description",
                    homepageUrl = "homepageUrl",
                    binaryArtifact = RemoteArtifact.EMPTY,
                    sourceArtifact = RemoteArtifact.EMPTY,
                    vcs = VcsInfo.EMPTY
            )

            val resultFile = createTempFile()
            val resultContent = "magic"

            // Put the file contents into the cache.
            resultFile.writeText(resultContent)
            cache.write(pkg, "test", resultFile) shouldBe true

            // Delete the original result file to ensure it gets re-created.
            resultFile.delete() shouldBe true

            // Get the file contents from the cache.
            cache.read(pkg, "test", resultFile) shouldBe true
            resultFile.readText() shouldEqual resultContent

            // Clean up.
            resultFile.delete() shouldBe true
        }
    }
}
