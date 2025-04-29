/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.clients.osv

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.TestConfiguration
import io.kotest.core.spec.style.StringSpec
import io.kotest.inspectors.forAll

import org.ossreviewtoolkit.utils.test.readResource

class ModelTest : StringSpec({
    "Deserializing and serializing any vulnerability is idempotent for all official examples" {
        getVulnerabilityExamplesJson().forAll { vulnerabilityJson ->
            val vulnerability = OsvService.JSON.decodeFromString<Vulnerability>(vulnerabilityJson)

            val serializedVulnerabilityJson = OsvService.JSON.encodeToString(vulnerability)

            serializedVulnerabilityJson shouldEqualJson vulnerabilityJson
        }
    }
})

private fun TestConfiguration.getVulnerabilityExamplesJson(): List<String> =
    (1..7).map { i -> readResource("/vulnerability-example-$i.json") }
