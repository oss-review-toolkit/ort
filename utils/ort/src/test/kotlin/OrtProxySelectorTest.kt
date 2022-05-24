/*
 * Copyright (C) 2020 Bosch.IO GmbH
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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

import java.net.URI

import org.ossreviewtoolkit.utils.common.temporaryProperties
import org.ossreviewtoolkit.utils.test.toGenericString

class OrtProxySelectorTest : WordSpec({
    "An added HTTP proxy" should {
        "be used for all HTTP URLs by default" {
            val selector = createProxySelector("http")

            selector.select(URI("http://fake-host-1")).map {
                it.toGenericString()
            }.single() shouldBe "HTTP @ fake-proxy:8080"

            selector.select(URI("http://fake-host-2:80")).map {
                it.toGenericString()
            }.single() shouldBe "HTTP @ fake-proxy:8080"
        }

        "not be used for HTTPS hosts" {
            val selector = createProxySelector("http")

            selector.select(URI("https://fake-host:443")) shouldBe OrtProxySelector.NO_PROXY_LIST
        }

        "not be used for no-proxy hosts" {
            temporaryProperties("http.proxyExcludes" to "fake-host") {
                val selector = createProxySelector("http")

                selector.select(URI("http://fake-host")) shouldBe OrtProxySelector.NO_PROXY_LIST
            }
        }

        "only be used for included hosts" {
            temporaryProperties("http.proxyIncludes" to "fake-host-1") {
                val selector = createProxySelector("http")

                selector.select(URI("http://fake-host-1")).map {
                    it.toGenericString()
                }.single() shouldBe "HTTP @ fake-proxy:8080"

                selector.select(URI("http://fake-host-2")) shouldBe OrtProxySelector.NO_PROXY_LIST
            }
        }
    }

    "An added HTTPS proxy" should {
        "be used for all HTTPS URLs by default" {
            val selector = createProxySelector("https")

            selector.select(URI("https://fake-host-1")).map {
                it.toGenericString()
            }.single() shouldBe "HTTP @ fake-proxy:8080"

            selector.select(URI("https://fake-host-2:443")).map {
                it.toGenericString()
            }.single() shouldBe "HTTP @ fake-proxy:8080"
        }

        "not be used for HTTP hosts" {
            val selector = createProxySelector("https")

            selector.select(URI("http://fake-host:80")) shouldBe OrtProxySelector.NO_PROXY_LIST
        }

        "not be used for no-proxy hosts" {
            temporaryProperties("https.proxyExcludes" to "fake-host") {
                val selector = createProxySelector("https")

                selector.select(URI("https://fake-host")) shouldBe OrtProxySelector.NO_PROXY_LIST
            }
        }

        "only be used for included hosts" {
            temporaryProperties("https.proxyIncludes" to "fake-host-1") {
                val selector = createProxySelector("https")

                selector.select(URI("https://fake-host-1")).map {
                    it.toGenericString()
                }.single() shouldBe "HTTP @ fake-proxy:8080"

                selector.select(URI("https://fake-host-2")) shouldBe OrtProxySelector.NO_PROXY_LIST
            }
        }
    }
})

private fun createProxySelector(protocol: String): OrtProxySelector {
    // Using a non-null assertion is fine here as we know the URL to be parsable.
    val proxy = determineProxyFromURL("http://fake-proxy:8080")!!

    return OrtProxySelector().removeAllProxies().addProxy("test", protocol, proxy)
}
