package com.here.provenanceanalyzer.scanner

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldNotBe
import io.kotlintest.matchers.shouldThrow
import io.kotlintest.specs.WordSpec

@Suppress("UnsafeCallOnNullableType", "UnsafeCast")
class ScanResultsCacheTest : WordSpec() {

    init {
        "ScanResultsCache.configure" should {
            "fail if the cache type is missing" {
                val exception = shouldThrow<IllegalArgumentException> {
                    ScanResultsCache.configure(mapOf())
                }
                exception.message shouldBe "Cache type is missing."
            }

            "fail if the cache type is unknown" {
                val exception = shouldThrow<IllegalArgumentException> {
                    ScanResultsCache.configure(mapOf("type" to "abcd"))
                }
                exception.message shouldBe "Cache type 'abcd' unknown."
            }

            "fail if the Artifactory URL is missing" {
                val exception = shouldThrow<IllegalArgumentException> {
                    ScanResultsCache.configure(mapOf(
                            "type" to "artifactory",
                            "apiToken" to "someApiToken"
                    ))
                }
                exception.message shouldBe "URL for Artifactory cache is missing."
            }

            "fail if the Artifactory apiToken is missing" {
                val exception = shouldThrow<IllegalArgumentException> {
                    ScanResultsCache.configure(mapOf(
                            "type" to "artifactory",
                            "url" to "someUrl"
                    ))
                }
                exception.message shouldBe "API token for Artifactory cache is missing."
            }

            "configure the Artifactory cache correctly" {
                ScanResultsCache.configure(mapOf(
                        "type" to "artifactory",
                        "apiToken" to "someApiToken",
                        "url" to "someUrl"
                ))

                ScanResultsCache.cache shouldNotBe null
                ScanResultsCache.cache::class shouldBe ArtifactoryCache::class
                (ScanResultsCache.cache as ArtifactoryCache).getApiToken() shouldBe "someApiToken"
                (ScanResultsCache.cache as ArtifactoryCache).getUrl() shouldBe "someUrl"
            }
        }
    }

    fun ArtifactoryCache.getStringField(name: String): String {
        javaClass.getDeclaredField(name).let {
            it.isAccessible = true
            return it.get(this) as String
        }
    }

    fun ArtifactoryCache.getApiToken(): String {
        return getStringField("apiToken")
    }

    fun ArtifactoryCache.getUrl(): String {
        return getStringField("url")
    }

}
