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

        if (response.second.statusCode == 200) {
            log.info { "Downloaded $cachePath from Artifactory cache." }
            return true
        } else {
            log.info {
                "Could not get $cachePath from Artifactory cache: ${response.second.statusCode} - " +
                        response.second.responseMessage
            }
            return false
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

        if (response.second.statusCode == 201) {
            log.info { "Uploaded $cachePath to Artifactory cache." }
            return true
        } else {
            log.info {
                "Could not upload $cachePath to artifactory cache: ${response.second.statusCode} - " +
                        response.second.responseMessage
            }
            return false
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
