package com.here.provenanceanalyzer.scanner

import ch.frankel.slf4k.info

import com.github.kittinunf.fuel.Fuel

import com.here.provenanceanalyzer.model.Package
import com.here.provenanceanalyzer.util.log

import java.io.File

class ArtifactoryCache(
        private val url: String,
        private val apiToken: String
) : ScanResultsCache {

    override fun read(pkg: Package, target: File): Boolean {
        val cachePath = cachePath(pkg, target)

        log.info { "Trying to read scan results from Artifactory cache: $cachePath" }

        val response = Fuel
                .download("$url/$cachePath")
                .header("X-JFrog-Art-Api" to apiToken)
                .destination { _, _ -> target }
                .responseString()

        return (response.second.statusCode == 200).also {
            log.info {
                if (it) {
                    "Downloaded $cachePath from Artifactory cache."
                } else {
                    "Could not get $cachePath from Artifactory cache: ${response.second.statusCode} - " +
                            response.second.responseMessage
                }
            }
        }
    }

    override fun write(pkg: Package, source: File): Boolean {
        val cachePath = cachePath(pkg, source)

        log.info { "Writing scan results to Artifactory cache: $cachePath" }

        val response = Fuel
                .put("$url/$cachePath")
                .header("X-JFrog-Art-Api" to apiToken)
                .body(source.readText())
                .responseString()

        return (response.second.statusCode == 201).also {
            log.info {
                if (it) {
                    "Uploaded $cachePath to Artifactory cache."
                } else {
                    "Could not upload $cachePath to artifactory cache: ${response.second.statusCode} - " +
                            response.second.responseMessage
                }
            }
        }
    }

    private fun cachePath(pkg: Package, resultsFile: File) =
            "scan-results/" +
                    "${pkg.packageManager.valueOrUnderscore()}/" +
                    "${pkg.namespace.valueOrUnderscore()}/" +
                    "${pkg.name.valueOrUnderscore()}/" +
                    "${pkg.version.valueOrUnderscore()}/" +
                    resultsFile.name

    private fun String?.valueOrUnderscore(): String {
        return if (this == null || this.isEmpty()) "_" else this
    }

}
