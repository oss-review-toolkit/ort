/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.clients.clearlydefined

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe

class CoordinatesTest : WordSpec({
    "Reconstructing coordinates from their string representation" should {
        "work for full coordinates" {
            val coords = Coordinates(
                type = ComponentType.MAVEN,
                provider = Provider.MAVEN_CENTRAL,
                namespace = "namespace",
                name = "name",
                revision = "0000000000000000000000000000000000000000"
            )

            Coordinates(coords.toString()) shouldBe coords
        }

        "work for coordinates without a namespace" {
            val coords = Coordinates(
                type = ComponentType.NPM,
                provider = Provider.NPM_JS,
                namespace = null,
                name = "name",
                revision = "main"
            )

            Coordinates(coords.toString()) shouldBe coords
        }

        "work for coordinates without a revision" {
            val coords = Coordinates(
                type = ComponentType.SOURCE_ARCHIVE,
                provider = Provider.MAVEN_CENTRAL,
                namespace = "namespace",
                name = "name",
                revision = null
            )

            Coordinates(coords.toString()) shouldBe coords
        }
    }
})
