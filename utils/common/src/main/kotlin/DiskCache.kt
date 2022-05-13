/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.utils.common

import com.jakewharton.disklrucache.DiskLruCache

import java.io.File
import java.io.IOException

import kotlin.math.pow

import org.apache.logging.log4j.kotlin.Logging

/**
 * Wrapper around [DiskLruCache] that adds a workaround for the 64 character key length limit.
 */
class DiskCache(
    /**
     * The directory to store the cache data, must be exclusive to the cache.
     */
    directory: File,

    /**
     * The maximum size of the disk cache in bytes.
     */
    maxCacheSizeInBytes: Long,

    /**
     * Duration in seconds that cache entries are valid.
     */
    private val maxCacheEntryAgeInSeconds: Int
) {
    companion object : Logging {
        const val INDEX_FULL_KEY = 0
        const val INDEX_TIMESTAMP = 1
        const val INDEX_DATA = 2
        const val VALUE_COUNT = 3

        /**
         * Maximum length for a key in [DiskLruCache].
         */
        const val MAX_KEY_LENGTH = 64

        /**
         * Length of the suffix appended to long keys.
         */
        const val KEY_SUFFIX_LENGTH = 6

        /**
         * The upper limit of the key index suffix.
         */
        val KEY_INDEX_LIMIT = 10.0.pow(KEY_SUFFIX_LENGTH - 1).toInt()

        /**
         * A regular expression to search for illegal characters in DiskLruCache key strings.
         */
        private val ILLEGAL_KEY_CHARS = Regex("[^a-z0-9_-]")
    }

    private val diskLruCache = DiskLruCache.open(directory, 0, VALUE_COUNT, maxCacheSizeInBytes)

    /**
     * Shorten the string to be usable as a key for [DiskLruCache] which has a maximum length of [MAX_KEY_LENGTH].
     * Shortening the keys will lead to conflicts, so append a serial number to the key and store the full key in the
     * cache, so that it is possible to store multiple entries for the same shortened key, and to check which of those
     * belongs to which full key.
     * If the string is shorter or equal to [MAX_KEY_LENGTH] - [KEY_SUFFIX_LENGTH] chars take it as is, otherwise
     * shorten it and append the serial number.
     */
    private fun String.asKey(): String {
        // DiskLruCache keys must match regex [a-z0-9_-]{1,64}.
        val mappedKey = lowercase().replace('.', '-').replace(ILLEGAL_KEY_CHARS, "_")

        if (mappedKey.length <= MAX_KEY_LENGTH - KEY_SUFFIX_LENGTH) {
            // String is short enough to be unique, use it as is.
            return mappedKey
        }

        // String is too long to be unique, append it with a serial number.
        val shortenedKey = mappedKey.chunkedSequence(MAX_KEY_LENGTH - KEY_SUFFIX_LENGTH).first()
        for (index in 0 until KEY_INDEX_LIMIT) {
            val tryKey = "$shortenedKey-" + "$index".padStart(KEY_SUFFIX_LENGTH - 1, '0')
            diskLruCache[tryKey].use { entry ->
                if (entry == null || entry.getString(INDEX_FULL_KEY) == this) {
                    return tryKey
                }
            }
        }

        throw IOException(
            "Cannot generate key for '$this' because all possible keys starting with '$shortenedKey' are taken."
        )
    }

    fun read(key: String): String? {
        val diskKey = key.asKey()
        try {
            diskLruCache[diskKey]?.use { entry ->
                val time = entry.getString(INDEX_TIMESTAMP).toLong()
                if (time + maxCacheEntryAgeInSeconds >= currentTimeInSeconds()) {
                    return entry.getString(INDEX_DATA)
                }
            }

            // Remove the expired entry after the snapshot was closed.
            diskLruCache.remove(diskKey)
        } catch (e: IOException) {
            logger.error { "Could not read cache entry for key '$diskKey': ${e.collectMessages()}" }
        }
        return null
    }

    fun write(key: String, data: String): Boolean {
        val diskKey = key.asKey()
        try {
            diskLruCache.edit(diskKey).apply {
                set(INDEX_FULL_KEY, key)
                set(INDEX_TIMESTAMP, currentTimeInSeconds().toString())
                set(INDEX_DATA, data)
                commit()
            }
            return true
        } catch (e: IOException) {
            logger.error { "Could not write to disk cache for key '$diskKey': ${e.collectMessages()}" }
        }
        return false
    }

    private fun currentTimeInSeconds() = System.currentTimeMillis() / 1000L
}
