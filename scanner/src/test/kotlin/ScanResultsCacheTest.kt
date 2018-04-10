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

import com.here.ort.model.yamlMapper

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldNotBe
import io.kotlintest.matchers.shouldThrow
import io.kotlintest.specs.WordSpec

@Suppress("UnsafeCallOnNullableType", "UnsafeCast")
class ScanResultsCacheTest : WordSpec() {

    private fun ArtifactoryCache.getStringField(name: String): String {
        javaClass.getDeclaredField(name).let {
            it.isAccessible = true
            return it.get(this) as String
        }
    }

    private fun ArtifactoryCache.getApiToken() = getStringField("apiToken")

    private fun ArtifactoryCache.getUrl() = getStringField("url")

    init {
        "ScanResultsCache.configure" should {
            "fail if the cache type is missing" {
                val exception = shouldThrow<IllegalArgumentException> {
                    val config = yamlMapper.readTree("""
                        scanner:
                          cache:
                    """)
                    ScanResultsCache.configure(config)
                }
                exception.message shouldBe "Cache type is missing."
            }

            "fail if the cache type is unknown" {
                val exception = shouldThrow<IllegalArgumentException> {
                    val config = yamlMapper.readTree("""
                        scanner:
                          cache:
                            type: abcd
                    """)
                    ScanResultsCache.configure(config)
                }
                exception.message shouldBe "Cache type 'abcd' unknown."
            }

            "fail if the Artifactory URL is missing" {
                val exception = shouldThrow<IllegalArgumentException> {
                    val config = yamlMapper.readTree("""
                        scanner:
                          cache:
                            type: Artifactory
                            apiToken: someApiToken
                    """)
                    ScanResultsCache.configure(config)
                }
                exception.message shouldBe "URL for Artifactory cache is missing."
            }

            "fail if the Artifactory apiToken is missing" {
                val exception = shouldThrow<IllegalArgumentException> {
                    val config = yamlMapper.readTree("""
                        scanner:
                          cache:
                            type: Artifactory
                            url: someUrl
                    """)
                    ScanResultsCache.configure(config)
                }
                exception.message shouldBe "API token for Artifactory cache is missing."
            }

            "configure the Artifactory cache correctly" {
                val config = yamlMapper.readTree("""
                        scanner:
                          cache:
                            type: Artifactory
                            apiToken: someApiToken
                            url: someUrl
                    """)
                ScanResultsCache.configure(config)

                ScanResultsCache.cache shouldNotBe null
                ScanResultsCache.cache::class shouldBe ArtifactoryCache::class
                (ScanResultsCache.cache as ArtifactoryCache).getApiToken() shouldBe "someApiToken"
                (ScanResultsCache.cache as ArtifactoryCache).getUrl() shouldBe "someUrl"
            }
        }
    }
}
