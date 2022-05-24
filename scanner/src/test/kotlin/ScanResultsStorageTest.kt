/*
 * Copyright (C) 2020 Bosch.IO GmbH
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

package org.ossreviewtoolkit.scanner

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.beInstanceOf

import java.io.File

import org.ossreviewtoolkit.model.config.ClearlyDefinedStorageConfiguration
import org.ossreviewtoolkit.model.config.FileBasedStorageConfiguration
import org.ossreviewtoolkit.model.config.FileStorageConfiguration
import org.ossreviewtoolkit.model.config.HttpFileStorageConfiguration
import org.ossreviewtoolkit.model.config.LocalFileStorageConfiguration
import org.ossreviewtoolkit.model.config.ScannerConfiguration
import org.ossreviewtoolkit.scanner.storages.ClearlyDefinedStorage
import org.ossreviewtoolkit.scanner.storages.CompositeStorage
import org.ossreviewtoolkit.scanner.storages.FileBasedStorage
import org.ossreviewtoolkit.scanner.storages.NoStorage
import org.ossreviewtoolkit.utils.ort.storage.HttpFileStorage
import org.ossreviewtoolkit.utils.ort.storage.LocalFileStorage

class ScanResultsStorageTest : WordSpec({
    "ScanResultsStorage.configure()" should {
        "use no storage if not configured" {
            ScanResultsStorage.storage should beInstanceOf<NoStorage>()
        }

        "configure a local file storage" {
            val fileStorageConfig = LocalFileStorageConfiguration(directory = File("."))
            val backendConfig = FileStorageConfiguration(localFileStorage = fileStorageConfig)
            val fileBasedStorageConfig = FileBasedStorageConfiguration(backendConfig)
            val config = ScannerConfiguration(
                storages = mapOf("local" to fileBasedStorageConfig),
                storageWriters = listOf("local")
            )

            val storage = ScanResultsStorage.configure(config)
            storage should beInstanceOf<FileBasedStorage>()
            val backend = (storage as FileBasedStorage).backend
            backend should beInstanceOf<LocalFileStorage>()
            (backend as LocalFileStorage).directory.absolutePath shouldBe fileStorageConfig.directory.absolutePath
        }

        "configure an HTTP file storage" {
            val httpStorageConfig = HttpFileStorageConfiguration(
                "https://some.storage.org/data",
                "?user=User",
                mapOf("Authorization" to "Bearer 1234567890")
            )
            val backendConfig = FileStorageConfiguration(httpFileStorage = httpStorageConfig)
            val fileBasedStorageConfig = FileBasedStorageConfiguration(backendConfig)
            val config = ScannerConfiguration(
                storages = mapOf("http" to fileBasedStorageConfig),
                storageReaders = listOf("http"),
                storageWriters = listOf("http")
            )

            val storage = ScanResultsStorage.configure(config)
            storage should beInstanceOf<FileBasedStorage>()
            val backend = (storage as FileBasedStorage).backend
            backend should beInstanceOf<HttpFileStorage>()
            (backend as HttpFileStorage).url shouldBe httpStorageConfig.url
        }

        "configure a ClearlyDefined storage" {
            val cdConfig = ClearlyDefinedStorageConfiguration("https://clearly-defined.org/data/")
            val config = ScannerConfiguration(
                storages = mapOf("clearly-defined" to cdConfig),
                storageReaders = listOf("clearly-defined"),
                storageWriters = listOf()
            )

            val storage = ScanResultsStorage.configure(config)
            storage should beInstanceOf<ClearlyDefinedStorage>()
            (storage as ClearlyDefinedStorage).configuration shouldBe cdConfig
        }

        "configure the default if no storages are specified" {
            ScanResultsStorage.configure(ScannerConfiguration())
            val storage = ScanResultsStorage.storage

            storage should beInstanceOf<FileBasedStorage>()
        }

        "order storage readers and writers correctly" {
            val fileStorageConfig = LocalFileStorageConfiguration(directory = File("."))
            val backendConfig = FileStorageConfiguration(localFileStorage = fileStorageConfig)
            val fileBasedStorageConfig = FileBasedStorageConfiguration(backendConfig)
            val cdConfig = ClearlyDefinedStorageConfiguration("https://clearly-defined.org/data/")
            val config = ScannerConfiguration(
                storages = mapOf("file" to fileBasedStorageConfig, "cd" to cdConfig),
                storageWriters = listOf("file", "cd"),
                storageReaders = listOf("cd", "file")
            )

            val storage = ScanResultsStorage.configure(config)
            storage should beInstanceOf<CompositeStorage>()
            (storage as CompositeStorage).readers shouldHaveSize 2
            val cdStorage = storage.readers[0]
            val fileStorage = storage.readers[1]
            cdStorage should beInstanceOf<ClearlyDefinedStorage>()
            fileStorage should beInstanceOf<FileBasedStorage>()
            storage.writers shouldContainExactly listOf(fileStorage, cdStorage)
        }

        "detect references to unknown storage readers" {
            val exception = shouldThrow<IllegalArgumentException> {
                val config = ScannerConfiguration(
                    storages = mapOf(
                        "cdProd" to ClearlyDefinedStorageConfiguration("http://prod.cd"),
                        "cdDev" to ClearlyDefinedStorageConfiguration("http://dev.cd")
                    ),
                    storageReaders = listOf("nonExistingReader")
                )
                ScanResultsStorage.configure(config)
            }

            exception.message shouldContain "'nonExistingReader'"
        }

        "detect references to unknown storage writers" {
            val exception = shouldThrow<IllegalArgumentException> {
                val config = ScannerConfiguration(
                    storages = mapOf(
                        "cdProd" to ClearlyDefinedStorageConfiguration("http://prod.cd"),
                        "cdDev" to ClearlyDefinedStorageConfiguration("http://dev.cd")
                    ),
                    storageWriters = listOf("nonExistingWriter")
                )
                ScanResultsStorage.configure(config)
            }

            exception.message shouldContain "'nonExistingWriter'"
        }
    }
})
