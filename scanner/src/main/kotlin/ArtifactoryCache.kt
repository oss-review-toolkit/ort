package com.here.ort.scanner

import ch.frankel.slf4k.info

import com.github.kittinunf.fuel.Fuel

import com.here.ort.model.Package
import com.here.ort.util.log

import java.io.File
import java.net.HttpURLConnection

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

        return (response.second.statusCode == HttpURLConnection.HTTP_OK).also {
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

        return (response.second.statusCode == HttpURLConnection.HTTP_CREATED).also {
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
