package com.here.ort.scanner

import com.here.ort.util.yamlMapper

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

    private fun ArtifactoryCache.getApiToken(): String {
        return getStringField("apiToken")
    }

    private fun ArtifactoryCache.getUrl(): String {
        return getStringField("url")
    }

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
