package com.here.ort.scanner

import ch.frankel.slf4k.*

import com.fasterxml.jackson.databind.JsonNode

import com.here.ort.model.Package
import com.here.ort.util.log

import java.io.File

interface ScanResultsCache {

    /**
     * Read a scan results file from the cache.
     *
     * @param pkg The package the scan results belong to.
     * @param target The local file to store the scan results in.
     */
    fun read(pkg: Package, target: File): Boolean

    /**
     * Write a scan results file to the cache.
     *
     * @param pkg The package the scan results belong to.
     * @param source The local file containing the scan results.
     */
    fun write(pkg: Package, source: File): Boolean

    companion object : ScanResultsCache {

        var cache = object : ScanResultsCache {
            override fun read(pkg: Package, target: File) = false
            override fun write(pkg: Package, source: File) = false
        }
            private set

        fun configure(config: JsonNode?) {
            // Return early if there is no cache configuration.
            val cacheNode = config?.get("scanner")?.get("cache") ?: return

            val type = cacheNode["type"]?.asText() ?: throw IllegalArgumentException("Cache type is missing.")

            when (type.toLowerCase()) {
                "artifactory" -> {
                    val apiToken = cacheNode["apiToken"]?.asText() ?:
                            throw IllegalArgumentException("API token for Artifactory cache is missing.")

                    val url = cacheNode["url"]?.asText() ?:
                            throw IllegalArgumentException("URL for Artifactory cache is missing.")

                    cache = ArtifactoryCache(url, apiToken)
                    log.info { "Using Artifactory cache '$url'." }
                }
                else -> throw IllegalArgumentException("Cache type '$type' unknown.")
            }
        }

        override fun read(pkg: Package, target: File) = cache.read(pkg, target)

        override fun write(pkg: Package, source: File) = cache.write(pkg, source)
    }

}
