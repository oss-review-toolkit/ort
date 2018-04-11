/*
 * Copyright (c) 2017-2018 HERE Europe B.V.
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

import com.here.ort.model.EMPTY_NODE
import com.here.ort.model.HashAlgorithm
import com.here.ort.model.Identifier
import com.here.ort.model.Provenance
import com.here.ort.model.RemoteArtifact
import com.here.ort.model.ScanResult
import com.here.ort.model.ScanSummary
import com.here.ort.model.ScannerSpecification
import com.here.ort.model.VcsInfo
import com.here.ort.model.jsonMapper

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer

import io.kotlintest.specs.StringSpec
import io.kotlintest.TestCaseContext
import io.kotlintest.matchers.contain
import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe

import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.time.Duration
import java.time.Instant

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

            super.interceptTestCase(context, test)
        } finally {
            // Ensure the server is properly stopped even in case of exceptions.
            server.stop(0)
        }
    }

    private val id = Identifier("provider", "namespace", "name", "version")

    private val downloadTime1 = Instant.EPOCH + Duration.ofDays(1)
    private val downloadTime2 = Instant.EPOCH + Duration.ofDays(2)
    private val downloadTime3 = Instant.EPOCH + Duration.ofDays(3)

    private val provenanceWithSourceArtifact = Provenance(
            downloadTime = downloadTime1,
            sourceArtifact = RemoteArtifact("url", "hash", HashAlgorithm.SHA1)
    )
    private val provenanceWithVcsInfo = Provenance(
            downloadTime = downloadTime2,
            vcsInfo = VcsInfo("type", "url", "revision", "path")
    )
    private val provenanceEmpty = Provenance(downloadTime3)

    private val scannerSpecs1 = ScannerSpecification("name 1", "version 1", "config 1")
    private val scannerSpecs2 = ScannerSpecification("name 2", "version 2", "config 2")

    private val scannerStartTime1 = downloadTime1 + Duration.ofMinutes(1)
    private val scannerEndTime1 = scannerStartTime1 + Duration.ofMinutes(1)
    private val scannerStartTime2 = downloadTime2 + Duration.ofMinutes(1)
    private val scannerEndTime2 = scannerStartTime2 + Duration.ofMinutes(1)

    private val scanSummaryWithFiles = ScanSummary(
            scannerStartTime1,
            scannerEndTime1,
            1,
            sortedSetOf("license 1.1", "license 1.2"),
            sortedSetOf("error 1.1", "error 1.2")
    )
    private val scanSummaryWithoutFiles = ScanSummary(
            scannerStartTime2,
            scannerEndTime2,
            0,
            sortedSetOf(),
            sortedSetOf()
    )

    private val rawResultWithContent = jsonMapper.readTree("\"key 1\": \"value 1\"")
    private val rawResultEmpty = EMPTY_NODE

    init {
        // TODO: Replace with tests for new  API

        "Scan result can be added to the cache" {
            val cache = ArtifactoryCache("http://${loopback.hostAddress}:$port", "apiToken")
            val scanResult = ScanResult(provenanceWithSourceArtifact, scannerSpecs1, scanSummaryWithFiles,
                    rawResultWithContent)

            val result = cache.add(id, scanResult)
            val cachedResults = cache.read(id)

            result shouldBe true
            cachedResults.id shouldBe id
            cachedResults.results.size shouldBe 1
            cachedResults.results[0] shouldBe scanResult
        }

        "Does not add scan result with fileCount 0 to cache" {
            val cache = ArtifactoryCache("http://${loopback.hostAddress}:$port", "apiToken")
            val scanResult = ScanResult(provenanceWithSourceArtifact, scannerSpecs1, scanSummaryWithoutFiles,
                    rawResultWithContent)

            val result = cache.add(id, scanResult)
            val cachedResults = cache.read(id)

            result shouldBe false
            cachedResults.id shouldBe id
            cachedResults.results.size shouldBe 0
        }

        "Does not add scan result without provenance information to cache" {
            val cache = ArtifactoryCache("http://${loopback.hostAddress}:$port", "apiToken")
            val scanResult = ScanResult(provenanceEmpty, scannerSpecs1, scanSummaryWithFiles,
                    rawResultEmpty)

            val result = cache.add(id, scanResult)
            val cachedResults = cache.read(id)

            result shouldBe false
            cachedResults.id shouldBe id
            cachedResults.results.size shouldBe 0
        }

        "Can retrieve all scan results from cache" {
            val cache = ArtifactoryCache("http://${loopback.hostAddress}:$port", "apiToken")
            val scanResult1 = ScanResult(provenanceWithSourceArtifact, scannerSpecs1, scanSummaryWithFiles,
                    rawResultWithContent)
            val scanResult2 = ScanResult(provenanceWithSourceArtifact, scannerSpecs2, scanSummaryWithFiles,
                    rawResultWithContent)

            val result1 = cache.add(id, scanResult1)
            val result2 = cache.add(id, scanResult2)
            val cachedResults = cache.read(id)

            result1 shouldBe true
            result2 shouldBe true
            cachedResults.results.size shouldBe 2
            cachedResults.results should contain(scanResult1)
            cachedResults.results should contain(scanResult2)
        }

        "Can retrieve all scan results for specific scanner from cache" {
            val cache = ArtifactoryCache("http://${loopback.hostAddress}:$port", "apiToken")
            val scanResult1 = ScanResult(provenanceWithSourceArtifact, scannerSpecs1, scanSummaryWithFiles,
                    rawResultWithContent)
            val scanResult2 = ScanResult(provenanceWithVcsInfo, scannerSpecs1, scanSummaryWithFiles,
                    rawResultWithContent)
            val scanResult3 = ScanResult(provenanceWithSourceArtifact, scannerSpecs2, scanSummaryWithFiles,
                    rawResultWithContent)

            val result1 = cache.add(id, scanResult1)
            val result2 = cache.add(id, scanResult2)
            val result3 = cache.add(id, scanResult3)
            val cachedResults = cache.read(id, scannerSpecs1)

            result1 shouldBe true
            result2 shouldBe true
            result3 shouldBe true
            cachedResults.results.size shouldBe 2
            cachedResults.results should contain(scanResult1)
            cachedResults.results should contain(scanResult2)
        }

//        "HTTP GET returns what was PUT" {
//            val cache = ArtifactoryCache("http://${loopback.hostAddress}:$port", "apiToken")
//
//            val pkg = Package(
//                    id = Identifier(
//                            provider = "provider",
//                            namespace = "namespace",
//                            name = "name",
//                            version = "version"
//                    ),
//                    declaredLicenses = sortedSetOf("license"),
//                    description = "description",
//                    homepageUrl = "homepageUrl",
//                    binaryArtifact = RemoteArtifact.EMPTY,
//                    sourceArtifact = RemoteArtifact.EMPTY,
//                    vcs = VcsInfo.EMPTY
//            )
//
//            val resultFile = createTempFile()
//            val resultContent = "magic"
//
//            // Put the file contents into the cache.
//            resultFile.writeText(resultContent)
//            cache.write(pkg, resultFile) shouldBe true
//
//            // Delete the original result file to ensure it gets re-created.
//            resultFile.delete() shouldBe true
//
//            // Get the file contents from the cache.
//            cache.read(pkg, resultFile) shouldBe true
//            resultFile.readText() shouldEqual resultContent
//
//            // Clean up.
//            resultFile.delete() shouldBe true
//        }
    }
}
