/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.analyzer.managers.utils

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beTheSameInstanceAs

import java.io.File

class SpdxDocumentCacheTest : WordSpec({
    "load" should {
        "return a parsed document" {
            val loader = SpdxDocumentCache()

            val document = loader.load(TEST_DOCUMENT)

            document.spdxId shouldBe "SPDXRef-DOCUMENT"
            document.name shouldBe "xyz-0.1.0"
        }

        "return a document from the cache" {
            val loader = SpdxDocumentCache()

            val document1 = loader.load(TEST_DOCUMENT)
            val document2 = loader.load(TEST_DOCUMENT)

            document1 should beTheSameInstanceAs(document2)
        }
    }
})

/** A test document that is loaded by the test cases. */
private val TEST_DOCUMENT =
    File("src/funTest/assets/projects/synthetic/spdx/project-xyz-with-inline-packages.spdx.yml").absoluteFile
