/*
 * Copyright (C) 2025 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
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

package org.ossreviewtoolkit.plugins.packagemanagers.gleam

import java.net.HttpURLConnection

import org.apache.logging.log4j.kotlin.logger

import org.ossreviewtoolkit.utils.ort.HttpDownloadError
import org.ossreviewtoolkit.utils.ort.OkHttpClientHelper
import org.ossreviewtoolkit.utils.ort.downloadText
import org.ossreviewtoolkit.utils.ort.okHttpClient

/**
 * Client for fetching package metadata from the Hex.pm API.
 */
internal class HexApiClient(private val baseUrl: String = "https://hex.pm") {
    private val packageCache = mutableMapOf<String, HexPackageInfo?>()
    private val userCache = mutableMapOf<String, HexUserInfo?>()

    /**
     * Fetch package information from Hex.pm API.
     * Results are cached to avoid duplicate API calls for the same package.
     * Returns null if the package is not found, rate limited, or on any other error.
     * Errors are logged but do not fail the analysis.
     */
    fun getPackageInfo(name: String): HexPackageInfo? =
        packageCache.getOrPut(name) {
            val url = "$baseUrl/api/packages/$name"

            okHttpClient.downloadText(url).mapCatching {
                parseHexModel<HexPackageInfo>(it)
            }.onFailure {
                handleError(it, "package '$name'")
            }.getOrNull()
        }

    /**
     * Fetch release information for a specific package version from Hex.pm API.
     * Returns null if the release is not found, rate limited, or on any other error.
     * Errors are logged but do not fail the analysis.
     */
    fun getReleaseInfo(name: String, version: String): HexReleaseInfo? {
        val url = "$baseUrl/api/packages/$name/releases/$version"

        return okHttpClient.downloadText(url).mapCatching {
            parseHexModel<HexReleaseInfo>(it)
        }.onFailure {
            handleError(it, "release '$name@$version'")
        }.getOrNull()
    }

    /**
     * Fetch user information from Hex.pm API.
     * Results are cached to avoid duplicate API calls for the same username.
     * Returns null if the user is not found, rate limited, or on any other error.
     * Errors are logged but do not fail the analysis.
     */
    fun getUserInfo(username: String): HexUserInfo? =
        userCache.getOrPut(username) {
            val url = "$baseUrl/api/users/$username"

            okHttpClient.downloadText(url).mapCatching {
                parseHexModel<HexUserInfo>(it)
            }.onFailure {
                handleError(it, "user '$username'")
            }.getOrNull()
        }

    private fun handleError(throwable: Throwable, context: String) {
        val error = (throwable as? HttpDownloadError) ?: run {
            logger.warn { "Unable to retrieve metadata for $context from Hex.pm: ${throwable.message}" }
            return
        }

        when (error.code) {
            HttpURLConnection.HTTP_NOT_FOUND -> {
                logger.debug { "The $context was not found on Hex.pm." }
            }

            OkHttpClientHelper.HTTP_TOO_MANY_REQUESTS -> {
                logger.warn {
                    "Hex.pm rate limit exceeded when requesting metadata for $context. " +
                        "Metadata will be incomplete."
                }
            }

            else -> {
                logger.warn {
                    "Hex.pm reported HTTP code ${error.code} when requesting metadata for $context."
                }
            }
        }
    }
}
