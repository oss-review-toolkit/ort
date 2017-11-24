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

import com.here.ort.model.CacheConfiguration
import com.here.ort.model.Configuration
import com.here.ort.model.ScannerConfiguration
import com.here.ort.utils.yamlMapper

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
            "fail if no cache is configured" {
                val config = CacheConfiguration(null)

                val exception = shouldThrow<IllegalArgumentException> {
                    ScanResultsCache.configure(config)
                }
                exception.message shouldBe "No cache configuration found."
            }

            "configure the Artifactory cache correctly" {
                val apiToken = "myApiToken"
                val url = "https://my.artifactory.url"
                val config = CacheConfiguration(com.here.ort.model.ArtifactoryCache(url, apiToken))

                ScanResultsCache.configure(config)

                ScanResultsCache.cache shouldNotBe null
                ScanResultsCache.cache::class shouldBe ArtifactoryCache::class
                (ScanResultsCache.cache as ArtifactoryCache).getApiToken() shouldBe apiToken
                (ScanResultsCache.cache as ArtifactoryCache).getUrl() shouldBe url
            }
        }
    }
}
