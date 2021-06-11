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

package org.ossreviewtoolkit.model.config

import io.kotest.core.spec.style.WordSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

import java.io.File

import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.model.writeValue
import org.ossreviewtoolkit.utils.test.createTestTempFile

class ScannerConfigurationTest : WordSpec({
    "ScannerConfiguration" should {
        "support a serialization round-trip via an ObjectMapper" {
            val ortConfig = OrtConfiguration.load(file = File("src/main/resources/reference.conf"))
            val rereadOrtConfig = createTestTempFile(suffix = ".yml").run {
                writeValue(ortConfig)
                readValue<OrtConfiguration>()
            }

            val actualScannerConfig = rereadOrtConfig.scanner
            val actualStorages = actualScannerConfig.storages.orEmpty()
            val expectedScannerConfig = ortConfig.scanner
            val expectedStorages = expectedScannerConfig.storages.orEmpty()

            // Note: loadedConfig cannot be directly compared to the original one, as there have been some changes:
            // Relative paths have been normalized, passwords do not get serialized, etc.
            actualScannerConfig.storageReaders shouldBe expectedScannerConfig.storageReaders
            actualScannerConfig.storageWriters shouldBe expectedScannerConfig.storageWriters
            actualScannerConfig.archive?.fileStorage?.httpFileStorage should beNull()

            actualStorages.keys shouldContainExactly expectedStorages.keys
            actualStorages.entries.forAll { (storageKey, storage) ->
                val orgStorage = expectedStorages.getOrDefault(storageKey, this)
                storage::class shouldBe orgStorage::class
            }
        }
    }
})
