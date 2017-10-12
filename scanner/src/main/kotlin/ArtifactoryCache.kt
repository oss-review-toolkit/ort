package com.here.ort.scanner

import ch.frankel.slf4k.info

import com.here.ort.model.Package
import com.here.ort.util.OkHttpClientHelper
import com.here.ort.util.log

import java.io.File
import java.net.HttpURLConnection

import okhttp3.Request

class ArtifactoryCache(
        private val url: String,
        private val apiToken: String
) : ScanResultsCache {

    override fun read(pkg: Package, target: File): Boolean {
        val cachePath = cachePath(pkg, target)

        log.info { "Trying to read scan results from Artifactory cache: $cachePath" }

        val request = Request.Builder()
                .header("X-JFrog-Art-Api", apiToken)
                .get()
                .url("$url/$cachePath")
                .build()

        return OkHttpClientHelper.execute("scanner", request).use { response ->
            (response.code() == HttpURLConnection.HTTP_OK).also {
                val message = if (it) {
                    response.body()?.let { target.writeBytes(it.bytes()) }
                    "Downloaded $cachePath from Artifactory cache."
                } else {
                    "Could not get $cachePath from Artifactory cache: ${response.code()} - " +
                            response.message()
                }

                log.info { message }
            }
        }
    }

    override fun write(pkg: Package, source: File): Boolean {
        val cachePath = cachePath(pkg, source)

        log.info { "Writing scan results to Artifactory cache: $cachePath" }

        val request = Request.Builder()
                .header("X-JFrog-Art-Api", apiToken)
                .put(OkHttpClientHelper.createRequestBody(source))
                .url("$url/$cachePath")
                .build()

        return OkHttpClientHelper.execute("scanner", request).use { response ->
            (response.code() == HttpURLConnection.HTTP_CREATED).also {
                log.info {
                    if (it) {
                        "Uploaded $cachePath to Artifactory cache."
                    } else {
                        "Could not upload $cachePath to artifactory cache: ${response.code()} - " +
                                response.message()
                    }
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
