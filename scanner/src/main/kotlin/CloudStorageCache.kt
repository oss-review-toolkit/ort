/*
 * Copyright (C) 2017-2018 HERE Europe B.V.
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

import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions

import com.here.ort.model.ScanResultContainer
import com.here.ort.model.ScanResult
import com.here.ort.model.Identifier
import com.here.ort.model.yamlMapper
import com.here.ort.model.config.CloudStorageCacheConfiguration
import com.here.ort.utils.log

import java.io.FileInputStream

class CloudStorageCache(
        private val config: CloudStorageCacheConfiguration
) : ScanStorage {
    private var storage: Storage

    init {
        if (System.getenv("GOOGLE_APPLICATION_CREDENTIALS") != null) {
            storage = StorageOptions.getDefaultInstance().service
        } else {
            require(config.googleApplicationCredentials != null) {
                "You should either specify path to Google Application Credentials json file in the environment or" +
                        " use --google-application-credentials command param"
            }

            storage = StorageOptions.newBuilder()
                    .setCredentials(
                            ServiceAccountCredentials.fromStream(FileInputStream(config.googleApplicationCredentials))
                    )
                    .build()
                    .getService()
        }
    }

    override fun read(id: Identifier): ScanResultContainer {
        log.info { "Trying to read scan results for '$id' from CloudStorage cache" }

        val bucketName = config.bucketName
        val bucket = storage.get(bucketName)
                ?: error("Bucket $bucketName does not exist")

        val blobName = id.toString()
        val blob = bucket.get(blobName)

        if (blob == null) {
            return ScanResultContainer(id, emptyList())
        } else {
            return yamlMapper.readValue(blob.getContent(), ScanResultContainer::class.java)
        }
    }

    override fun add(id: Identifier, scanResult: ScanResult): Boolean {
        val scanResults = ScanResultContainer(id, read(id).results + scanResult)

        val tempFile = createTempFile("scan-results-")
        yamlMapper.writeValue(tempFile, scanResults)

        val bucketName = config.bucketName
        val bucket = storage.get(bucketName)
                ?: error("Bucket $bucketName does not exist.")

        val blobName = id.toString()
        bucket.create(blobName, tempFile.readBytes())
        println("$blobName was successfully uploaded to bucket $bucketName.")
        return true
    }
}
