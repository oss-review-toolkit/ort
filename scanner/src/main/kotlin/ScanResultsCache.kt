package com.here.provenanceanalyzer.scanner

import ch.frankel.slf4k.*

import com.here.provenanceanalyzer.model.Package
import com.here.provenanceanalyzer.util.log

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

        fun configure(configuration: Map<String, String>) {
            val type = configuration["type"] ?: throw IllegalArgumentException("Cache type is missing.")

            when (type.toLowerCase()) {
                "artifactory" -> {
                    val apiToken = configuration["apiToken"] ?:
                            throw IllegalArgumentException("API token for Artifactory cache is missing.")

                    val url = configuration["url"] ?:
                            throw IllegalArgumentException("URL for Artifactory cache is missing.")

                    cache = ArtifactoryCache(url, apiToken)
                    log.info { "Using Artifactory cache '$url'." }
                }
                else -> throw IllegalArgumentException("Cache type '${configuration["type"]}' unknown.")
            }
        }

        override fun read(pkg: Package, target: File) = cache.read(pkg, target)

        override fun write(pkg: Package, source: File) = cache.write(pkg, source)
    }

}
