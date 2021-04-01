/*
 * Copyright (C) 2021 Bosch.IO GmbH
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

package org.ossreviewtoolkit.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

import java.io.File

class HashAlgorithmTest : StringSpec({
    // Use a file that is always checked out with Unix line endings in the working tree to get the same file contents on
    // all platforms and in the Git object store.
    val file = File("../LICENSE")

    "Calculating the SHA1 on a file should yield the correct result" {
        // The expected hash was calculated with "sha1sum".
        HashAlgorithm.SHA1.calculate(file) shouldBe "92170cdc034b2ff819323ff670d3b7266c8bffcd"
    }

    "Calculating the SHA1-GIT on a file should yield the correct result" {
        // The expected hash was calculated with "git ls-tree HEAD".
        HashAlgorithm.SHA1_GIT.calculate(file) shouldBe "8dada3edaf50dbc082c9a125058f25def75e625a"
    }

    "Calculating the SHA1-GIT on a resource should yield the correct result" {
        // The expected hash was calculated with "git ls-tree HEAD".
        HashAlgorithm.SHA1_GIT.calculate("/licenses/Apache-2.0") shouldBe
                "261eeb9e9f8b2b4b0d119366dda99c6fd7d35c64"
    }
})
