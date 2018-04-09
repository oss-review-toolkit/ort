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

import ch.frankel.slf4k.*

import com.here.ort.model.Configuration
import com.here.ort.model.Package
import com.here.ort.utils.log

import java.io.File

data class CacheStatistics(
    var numReads: Int = 0,
    var numHits: Int = 0
)

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

        val cache by lazy {
            createCacheFromConfiguration()
        }

        internal fun createCacheFromConfiguration(): ScanResultsCache {
            return Configuration.value.scanner?.cache.let { cache ->
                val noopCache = object : ScanResultsCache {
                    override fun read(pkg: Package, target: File) = false
                    override fun write(pkg: Package, source: File) = false
                }

                if (cache != null) {
                    when {
                        cache.artifactory != null -> {
                            cache.artifactory!!.let {
                                log.info { "Using Artifactory cache '${it.url}'." }
                                ArtifactoryCache(it.url, it.apiToken)
                            }
                        }
                        else -> {
                            log.debug { "No scan results cache configuration found." }
                            noopCache
                        }
                    }
                } else {
                    noopCache
                }
            }
        }

        var stats = CacheStatistics()

        override fun read(pkg: Package, target: File) = cache.read(pkg, target).also {
            ++stats.numReads
            if (it) {
                ++stats.numHits
            }
        }

        override fun write(pkg: Package, source: File) = cache.write(pkg, source)
    }
}
