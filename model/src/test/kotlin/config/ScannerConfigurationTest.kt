/*
 * Copyright (C) 2020 Bosch.IO GmbH
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

package org.ossreviewtoolkit.model.config

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.io.File

import kotlin.io.path.createTempFile

import org.ossreviewtoolkit.model.mapper
import org.ossreviewtoolkit.model.readValue

class ScannerConfigurationTest : WordSpec({
    "ScannerConfiguration" should {
        "support a serialization round-trip via an ObjectMapper" {
            val refConfig = File("src/main/resources/reference.conf")
            val ortConfig = OrtConfiguration.load(file = refConfig)
            val file = createTempFile(suffix = ".yml").toFile().apply { deleteOnExit() }

            file.mapper().writeValue(file, ortConfig.scanner)
            val loadedConfig = file.readValue<ScannerConfiguration>()

            // Note: loadedConfig cannot be directly compared to the original one, as there have been some changes:
            // Relative paths have been normalized, passwords do not get serialized, etc.
            loadedConfig.storageReaders shouldBe ortConfig.scanner.storageReaders
            loadedConfig.storageWriters shouldBe ortConfig.scanner.storageWriters
            loadedConfig.archive?.fileStorage?.httpFileStorage should beNull()

            val loadedStorages = loadedConfig.storages.orEmpty()
            val orgStorages = ortConfig.scanner.storages.orEmpty()
            loadedStorages.keys shouldContainExactly orgStorages.keys
            loadedStorages.forEach { e ->
                val orgStorage = orgStorages[e.key] ?: this
                e.value::class shouldBe orgStorage::class
            }
        }
    }
})
