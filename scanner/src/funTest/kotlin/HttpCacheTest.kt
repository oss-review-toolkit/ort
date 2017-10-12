package com.here.ort.scanner

import com.here.ort.model.Package

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
                    "packageManager",
                    "namespace",
                    "name",
                    "version",
                    "description",
                    "homepageUrl",
                    "downloadUrl",
                    "hash",
                    "hashAlgorithm",
                    "vcsPath",
                    "vcsProvider",
                    "vcsUrl",
                    "vcsRevision"
            )

            val resultFile = createTempFile()
            val resultContent = "magic"

            // Put the file contents into the cache.
            resultFile.writeText(resultContent)
            cache.write(pkg, resultFile) shouldBe true

            // Delete the original result file to ensure it gets re-created.
            resultFile.delete() shouldBe true

            // Get the file contents from the cache.
            cache.read(pkg, resultFile) shouldBe true
            resultFile.readText() shouldEqual resultContent

            // Clean up.
            resultFile.delete() shouldBe true
        }
    }
}
