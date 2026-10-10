/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.plugins.packagemanagers.python.utils

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.containExactly
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import org.ossreviewtoolkit.plugins.packagemanagers.python.PylockFile

private const val WHEEL_PATH = "/files/attrs-26.1.0-py3-none-any.whl"
private const val SDIST_PATH = "/files/attrs-26.1.0.tar.gz"

private const val CORE_METADATA = """Metadata-Version: 2.4
Name: attrs
Version: 26.1.0
Summary: Classes Without Boilerplate
License-Expression: MIT
"""

class PythonIndexClientTest : WordSpec({
    val server = WireMockServer(WireMockConfiguration.options().dynamicPort())

    beforeSpec { server.start() }
    beforeEach { server.resetAll() }
    afterSpec { server.stop() }

    fun indexUrl() = "${server.baseUrl()}/simple"

    fun attrs(wheel: PylockFile.Distribution, sdist: PylockFile.Distribution? = null, index: String? = indexUrl()) =
        PylockFile.Package(name = "attrs", version = "26.1.0", index = index, sdist = sdist, wheels = listOf(wheel))

    "getMetadata()" should {
        "retrieve the metadata file next to a locked wheel" {
            server.stubFor(
                get(urlPathEqualTo("$WHEEL_PATH.metadata"))
                    .willReturn(aResponse().withStatus(200).withBody(CORE_METADATA))
            )

            val pkg = attrs(PylockFile.Distribution(url = "${server.baseUrl()}$WHEEL_PATH"))

            val metadata = PythonIndexClient().getMetadata(pkg).shouldNotBeNull()

            metadata.summary shouldBe "Classes Without Boilerplate"
            metadata.licenseExpression shouldBe "MIT"
            metadata.source shouldBe "${server.baseUrl()}$WHEEL_PATH.metadata"
        }

        "fall back to the metadata file next to the source distribution" {
            server.stubFor(get(urlPathEqualTo("$WHEEL_PATH.metadata")).willReturn(aResponse().withStatus(404)))
            server.stubFor(
                get(urlPathEqualTo("$SDIST_PATH.metadata"))
                    .willReturn(aResponse().withStatus(200).withBody(CORE_METADATA))
            )

            val pkg = attrs(
                wheel = PylockFile.Distribution(url = "${server.baseUrl()}$WHEEL_PATH"),
                sdist = PylockFile.Distribution(url = "${server.baseUrl()}$SDIST_PATH")
            )

            val metadata = PythonIndexClient().getMetadata(pkg).shouldNotBeNull()

            metadata.source shouldBe "${server.baseUrl()}$SDIST_PATH.metadata"
        }

        "fall back to the JSON API of the index recorded in the lockfile" {
            server.stubFor(get(urlPathEqualTo("$WHEEL_PATH.metadata")).willReturn(aResponse().withStatus(404)))
            server.stubFor(
                get(urlPathEqualTo("/pypi/attrs/26.1.0/json"))
                    .willReturn(
                        aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(
                            """
                                {
                                  "info": {
                                    "author": null,
                                    "author_email": "Hynek Schlawack <hs@ox.cx>",
                                    "classifiers": ["License :: OSI Approved :: MIT License"],
                                    "home_page": null,
                                    "license": null,
                                    "license_expression": "MIT",
                                    "project_urls": {"GitHub": "https://github.com/python-attrs/attrs"},
                                    "summary": "Classes Without Boilerplate",
                                    "version": "26.1.0"
                                  },
                                  "urls": []
                                }
                            """.trimIndent()
                        )
                    )
            )

            val pkg = attrs(PylockFile.Distribution(url = "${server.baseUrl()}$WHEEL_PATH"), index = "${indexUrl()}/")

            val metadata = PythonIndexClient().getMetadata(pkg).shouldNotBeNull()

            metadata.summary shouldBe "Classes Without Boilerplate"
            metadata.licenseExpression shouldBe "MIT"
            metadata.authorEmail shouldBe "Hynek Schlawack <hs@ox.cx>"
            metadata.projectUrls shouldBe mapOf("GitHub" to "https://github.com/python-attrs/attrs")
            metadata.classifiers should containExactly("License :: OSI Approved :: MIT License")
            metadata.source shouldBe "${server.baseUrl()}/pypi/attrs/26.1.0/json"
        }

        "look up the file URLs on the JSON simple index for a lockfile that records paths only" {
            server.stubFor(
                get(urlPathEqualTo("/simple/attrs/"))
                    .withHeader("Accept", equalTo("application/vnd.pypi.simple.v1+json, text/html;q=0.1"))
                    .willReturn(
                        aResponse().withStatus(200)
                            .withHeader("Content-Type", "application/vnd.pypi.simple.v1+json")
                            .withBody(
                                """
                                    {
                                      "files": [
                                        {
                                          "filename": "attrs-25.1.0-py3-none-any.whl",
                                          "url": "${server.baseUrl()}/files/attrs-25.1.0-py3-none-any.whl",
                                          "core-metadata": true
                                        },
                                        {
                                          "filename": "attrs-26.1.0.tar.gz",
                                          "url": "${server.baseUrl()}$SDIST_PATH",
                                          "core-metadata": false
                                        },
                                        {
                                          "filename": "attrs-26.1.0-py3-none-any.whl",
                                          "url": "${server.baseUrl()}$WHEEL_PATH",
                                          "core-metadata": {"sha256": "4cd4"}
                                        }
                                      ],
                                      "name": "attrs"
                                    }
                                """.trimIndent()
                            )
                    )
            )

            server.stubFor(
                get(urlPathEqualTo("$WHEEL_PATH.metadata"))
                    .willReturn(aResponse().withStatus(200).withBody(CORE_METADATA))
            )

            val pkg = attrs(
                wheel = PylockFile.Distribution(name = "attrs-26.1.0-py3-none-any.whl", path = "wheels/attrs.whl"),
                sdist = PylockFile.Distribution(path = "sdists/attrs-26.1.0.tar.gz")
            )

            val metadata = PythonIndexClient().getMetadata(pkg).shouldNotBeNull()

            metadata.source shouldBe "${server.baseUrl()}$WHEEL_PATH.metadata"
            server.verify(0, getRequestedFor(urlPathEqualTo("$SDIST_PATH.metadata")))
        }

        "resolve relative file URLs on the JSON simple index against the project page" {
            server.stubFor(
                get(urlPathEqualTo("/simple/attrs/"))
                    .willReturn(
                        aResponse().withStatus(200)
                            .withHeader("Content-Type", "application/vnd.pypi.simple.v1+json")
                            .withBody(
                                """
                                    {
                                      "files": [
                                        {
                                          "filename": "attrs-26.1.0-py3-none-any.whl",
                                          "url": "../..$WHEEL_PATH",
                                          "core-metadata": true
                                        }
                                      ],
                                      "name": "attrs"
                                    }
                                """.trimIndent()
                            )
                    )
            )

            server.stubFor(
                get(urlPathEqualTo("$WHEEL_PATH.metadata"))
                    .willReturn(aResponse().withStatus(200).withBody(CORE_METADATA))
            )

            val pkg = attrs(PylockFile.Distribution(name = "attrs-26.1.0-py3-none-any.whl", path = "wheels/attrs.whl"))

            val metadata = PythonIndexClient().getMetadata(pkg).shouldNotBeNull()

            metadata.source shouldBe "${server.baseUrl()}$WHEEL_PATH.metadata"
        }

        "look up the file URLs on the HTML simple index for a lockfile that records paths only" {
            server.stubFor(
                get(urlPathEqualTo("/simple/attrs/"))
                    .willReturn(
                        aResponse().withStatus(200).withHeader("Content-Type", "text/html").withBody(
                            """
                                <!DOCTYPE html>
                                <html>
                                  <body>
                                    <a href="../../files/attrs-26.1.0.tar.gz#sha256=d03c">attrs-26.1.0.tar.gz</a><br />
                                    <a href="${server.baseUrl()}$WHEEL_PATH#sha256=c647" data-core-metadata="sha256=4cd4"
                                       data-requires-python="&gt;=3.9">attrs-26.1.0-py3-none-any.whl</a><br />
                                  </body>
                                </html>
                            """.trimIndent()
                        )
                    )
            )

            server.stubFor(
                get(urlPathEqualTo("$WHEEL_PATH.metadata"))
                    .willReturn(aResponse().withStatus(200).withBody(CORE_METADATA))
            )

            val pkg = attrs(PylockFile.Distribution(name = "attrs-26.1.0-py3-none-any.whl", path = "wheels/attrs.whl"))

            val metadata = PythonIndexClient().getMetadata(pkg).shouldNotBeNull()

            metadata.source shouldBe "${server.baseUrl()}$WHEEL_PATH.metadata"
        }

        "accept single-quoted and unquoted attributes on the HTML simple index" {
            server.stubFor(
                get(urlPathEqualTo("/simple/attrs/"))
                    .willReturn(
                        aResponse().withStatus(200).withHeader("Content-Type", "text/html").withBody(
                            """
                                <html><body>
                                <a href='../..$WHEEL_PATH#sha256=c647' data-core-metadata=true>
                                  attrs-26.1.0-py3-none-any.whl
                                </a>
                                </body></html>
                            """.trimIndent()
                        )
                    )
            )

            server.stubFor(
                get(urlPathEqualTo("$WHEEL_PATH.metadata"))
                    .willReturn(aResponse().withStatus(200).withBody(CORE_METADATA))
            )

            val pkg = attrs(PylockFile.Distribution(name = "attrs-26.1.0-py3-none-any.whl", path = "wheels/attrs.whl"))

            val metadata = PythonIndexClient().getMetadata(pkg).shouldNotBeNull()

            metadata.source shouldBe "${server.baseUrl()}$WHEEL_PATH.metadata"
        }

        "use a configured index instead of the index and file URLs recorded in the lockfile" {
            // The lockfile records PyPI and a file URL on the test server that must not be contacted.
            server.stubFor(
                get(urlPathEqualTo("/pypi-files$WHEEL_PATH.metadata")).willReturn(aResponse().withStatus(500))
            )
            server.stubFor(
                get(urlPathEqualTo("/proxy/simple/attrs/"))
                    .willReturn(
                        aResponse().withStatus(200)
                            .withHeader("Content-Type", "application/vnd.pypi.simple.v1+json")
                            .withBody(
                                """
                                    {
                                      "files": [
                                        {
                                          "filename": "attrs-26.1.0-py3-none-any.whl",
                                          "url": "${server.baseUrl()}/proxy$WHEEL_PATH",
                                          "core-metadata": true
                                        }
                                      ],
                                      "name": "attrs"
                                    }
                                """.trimIndent()
                            )
                    )
            )
            server.stubFor(
                get(urlPathEqualTo("/proxy$WHEEL_PATH.metadata"))
                    .willReturn(aResponse().withStatus(200).withBody(CORE_METADATA))
            )

            val pkg = attrs(
                PylockFile.Distribution(url = "${server.baseUrl()}/pypi-files$WHEEL_PATH"),
                index = "https://pypi.org/simple"
            )

            val metadata = PythonIndexClient("${server.baseUrl()}/proxy/simple").getMetadata(pkg).shouldNotBeNull()

            metadata.source shouldBe "${server.baseUrl()}/proxy$WHEEL_PATH.metadata"
            server.verify(0, getRequestedFor(urlPathEqualTo("/pypi-files$WHEEL_PATH.metadata")))
        }

        "return null for a malformed URL instead of failing" {
            val pkg = attrs(PylockFile.Distribution(url = "files$WHEEL_PATH"), index = "not a url")

            PythonIndexClient().getMetadata(pkg) should beNull()
        }

        "honor the legacy metadata key on the JSON simple index" {
            server.stubFor(
                get(urlPathEqualTo("/simple/attrs/"))
                    .willReturn(
                        aResponse().withStatus(200)
                            .withHeader("Content-Type", "application/vnd.pypi.simple.v1+json")
                            .withBody(
                                """
                                    {
                                      "files": [
                                        {
                                          "filename": "attrs-26.1.0-py3-none-any.whl",
                                          "url": "${server.baseUrl()}$WHEEL_PATH",
                                          "data-dist-info-metadata": true
                                        }
                                      ],
                                      "name": "attrs"
                                    }
                                """.trimIndent()
                            )
                    )
            )

            server.stubFor(
                get(urlPathEqualTo("$WHEEL_PATH.metadata"))
                    .willReturn(aResponse().withStatus(200).withBody(CORE_METADATA))
            )

            val pkg = attrs(PylockFile.Distribution(name = "attrs-26.1.0-py3-none-any.whl", path = "wheels/attrs.whl"))

            val metadata = PythonIndexClient().getMetadata(pkg).shouldNotBeNull()

            metadata.source shouldBe "${server.baseUrl()}$WHEEL_PATH.metadata"
        }

        "honor the legacy metadata attribute on the HTML simple index" {
            server.stubFor(
                get(urlPathEqualTo("/simple/attrs/"))
                    .willReturn(
                        aResponse().withStatus(200).withHeader("Content-Type", "text/html").withBody(
                            """
                                <a href="${server.baseUrl()}$WHEEL_PATH" data-dist-info-metadata="sha256=4cd4">
                                  attrs-26.1.0-py3-none-any.whl
                                </a>
                            """.trimIndent()
                        )
                    )
            )

            server.stubFor(
                get(urlPathEqualTo("$WHEEL_PATH.metadata"))
                    .willReturn(aResponse().withStatus(200).withBody(CORE_METADATA))
            )

            val pkg = attrs(PylockFile.Distribution(name = "attrs-26.1.0-py3-none-any.whl", path = "wheels/attrs.whl"))

            val metadata = PythonIndexClient().getMetadata(pkg).shouldNotBeNull()

            metadata.source shouldBe "${server.baseUrl()}$WHEEL_PATH.metadata"
        }

        "return null if the metadata file request fails and the JSON API response is malformed" {
            server.stubFor(get(urlPathEqualTo("$WHEEL_PATH.metadata")).willReturn(aResponse().withStatus(500)))
            server.stubFor(
                get(urlPathEqualTo("/pypi/attrs/26.1.0/json"))
                    .willReturn(aResponse().withStatus(200).withBody("{ \"info\": \"not an object\" }"))
            )

            val pkg = attrs(PylockFile.Distribution(url = "${server.baseUrl()}$WHEEL_PATH"))

            PythonIndexClient().getMetadata(pkg) should beNull()
            server.verify(1, getRequestedFor(urlPathEqualTo("/pypi/attrs/26.1.0/json")))
        }

        "return null if the metadata is not available anywhere" {
            server.stubFor(get(urlPathEqualTo("$WHEEL_PATH.metadata")).willReturn(aResponse().withStatus(404)))
            server.stubFor(get(urlPathEqualTo("/pypi/attrs/26.1.0/json")).willReturn(aResponse().withStatus(404)))

            val pkg = attrs(PylockFile.Distribution(url = "${server.baseUrl()}$WHEEL_PATH"))

            PythonIndexClient().getMetadata(pkg) should beNull()
        }

        "return null for packages that do not come from an index" {
            val pkg = PylockFile.Package(
                name = "sampleproject",
                version = "4.0.0",
                vcs = PylockFile.Vcs(type = "git", url = "https://github.com/pypa/sampleproject", commitId = "621e")
            )

            PythonIndexClient().getMetadata(pkg) should beNull()
            server.verify(0, getRequestedFor(urlPathEqualTo("/pypi/sampleproject/4.0.0/json")))
        }

        "cache the metadata per package" {
            server.stubFor(
                get(urlPathEqualTo("$WHEEL_PATH.metadata"))
                    .willReturn(aResponse().withStatus(200).withBody(CORE_METADATA))
            )

            val pkg = attrs(PylockFile.Distribution(url = "${server.baseUrl()}$WHEEL_PATH"))
            val client = PythonIndexClient()

            client.getMetadata(pkg) shouldBe client.getMetadata(pkg)
            server.verify(1, getRequestedFor(urlPathEqualTo("$WHEEL_PATH.metadata")))
        }
    }
})
