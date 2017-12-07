/*
 * Copyright (c) 2017 HERE Europe B.V.
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

package com.here.ort.util

import ch.frankel.slf4k.*

import com.jakewharton.disklrucache.DiskLruCache

import java.io.File
import java.io.IOException

/**
 * Wrapper around [DiskLruCache] that adds a workaround for the 64 character key length limit.
 */
class DiskCache(
        /**
         * The directory to store the cache data, must be exclusive to the cache.
         */
        val directory: File,

        /**
         * The maximum size of the disk cache.
         */
        val maxSize: Long,

        /**
         * Duration in seconds that cache entries are valid.
         */
        val timeToLive: Int
) {
    companion object {
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
    }

    private val diskLruCache = DiskLruCache.open(directory, 0, VALUE_COUNT, maxSize)

    /**
     * Shorten the string to be usable as a key for [DiskLruCache] which has a maximum length of [MAX_KEY_LENGTH].
     * Shortening the keys will lead to conflicts, so append a serial number to the key and store the full key in the
     * cache, so that it is possible to store multiple entries for the same shortened key, and to check which of those
     * belongs to which full key.
     * If the string is shorter or equal to [MAX_KEY_LENGTH] - [KEY_SUFFIX_LENGTH] chars take it as is, otherwise
     * shorten it and append the serial number.
     */
    private fun String.asKey(): String {
        return if (length <= MAX_KEY_LENGTH - KEY_SUFFIX_LENGTH) {
            // String is short enough to be unique, use it as is.
            this
        } else {
            // String is too long to be unique, append it with a serial number.
            val key = substring(0..minOf(MAX_KEY_LENGTH - KEY_SUFFIX_LENGTH - 1, length - 1))
            for (index in 0..99999) {
                val tryKey = "$key-" + "$index".padStart(KEY_SUFFIX_LENGTH - 1, '0')
                val entry = diskLruCache.get(tryKey)
                if (entry == null || entry.getString(INDEX_FULL_KEY) == this) {
                    return tryKey
                }
            }
            throw IOException(
                    "Cannot generate key for '$this' because all possible keys starting with '$key' are taken.")
        }
    }

    fun read(key: String): String? {
        try {
            diskLruCache.get(key.asKey())?.use { entry ->
                val time = entry.getString(INDEX_TIMESTAMP).toLong()
                if (time + timeToLive < timeInSeconds()) {
                    diskLruCache.remove(key)
                } else {
                    return entry.getString(INDEX_DATA)
                }
            }
        } catch (e: IOException) {
            log.error { "Could not read cache entry for key '$key': ${e.message}" }
        }
        return null
    }

    fun write(key: String, data: String): Boolean {
        try {
            diskLruCache.edit(key.asKey()).apply {
                set(INDEX_FULL_KEY, key)
                set(INDEX_TIMESTAMP, timeInSeconds().toString())
                set(INDEX_DATA, data)
                commit()
                return true
            }
        } catch (e: IOException) {
            log.error { "Could not write to disk cache for key '$key': ${e.message}" }
        }
        return false
    }

    private fun timeInSeconds() = System.currentTimeMillis() / 1000L
}
