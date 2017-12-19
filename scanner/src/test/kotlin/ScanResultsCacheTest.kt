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

import com.here.ort.model.Configuration

import io.kotlintest.matchers.include
import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldNotBe
import io.kotlintest.specs.StringSpec
import kotlin.reflect.jvm.jvmName

@Suppress("UnsafeCallOnNullableType", "UnsafeCast")
class ScanResultsCacheTest : StringSpec() {

    private fun ArtifactoryCache.getStringField(name: String): String {
        javaClass.getDeclaredField(name).let {
            it.isAccessible = true
            return it.get(this) as String
        }
    }

    private fun ArtifactoryCache.getApiToken() = getStringField("apiToken")

    private fun ArtifactoryCache.getUrl() = getStringField("url")

    init {
        "Use noop cache if no cache is configured" {
            Configuration.parse("""
                        scanner:
                          cache:
                        """.trimIndent())
            val cache = ScanResultsCache.createCacheFromConfiguration()

            cache::class shouldNotBe ArtifactoryCache::class
            cache::class.jvmName should include("noopCache")
        }

        "Configure the Artifactory cache correctly" {
            val apiToken = "myApiToken"
            val url = "https://my.artifactory.url"
            Configuration.parse("""
                        scanner:
                          cache:
                            artifactory:
                              apiToken: $apiToken
                              url: $url
                        """.trimIndent())
            val cache = ScanResultsCache.createCacheFromConfiguration()

            cache::class shouldBe ArtifactoryCache::class
            (cache as ArtifactoryCache).getApiToken() shouldBe apiToken
            cache.getUrl() shouldBe url
        }
    }
}
