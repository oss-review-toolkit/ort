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

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.containAll
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.should

private val VULNERABILITY_FOR_PACKAGE_BY_COMMIT_REQUEST = VulnerabilitiesForPackageRequest(
    commit = "6879efc2c1596d11a6a6ad296f80063b558d5e0f"
)
private val VULNERABILITY_FOR_PACKAGE_BY_NAME_AND_VERSION = VulnerabilitiesForPackageRequest(
    pkg = Package(
        name = "harfbuzz",
        ecosystem = "OSS-Fuzz"
    ),
    version = "2.2.0"
)
private val VULNERABILITY_FOR_PACKAGE_BY_INVALID_COMMIT_REQUEST = VulnerabilitiesForPackageRequest(
    commit = "6879efc2c1596d11a6a6ad296f80063b558d5e0c"
)

class OsvServiceWrapperFunTest : WordSpec({
    "getVulnerabilityIdsForPackages()" should {
        "return the vulnerability IDs for the given batch request" {
            val requests = listOf(
                VULNERABILITY_FOR_PACKAGE_BY_COMMIT_REQUEST,
                VULNERABILITY_FOR_PACKAGE_BY_INVALID_COMMIT_REQUEST,
                VULNERABILITY_FOR_PACKAGE_BY_NAME_AND_VERSION
            )

            val result = OsvServiceWrapper().getVulnerabilityIdsForPackages(requests)

            result shouldBeSuccess {
                it shouldHaveSize 3

                it[0] should containAll(
                    "CVE-2021-45931",
                    "CVE-2022-33068",
                    "CVE-2023-25193",
                    "CVE-2024-56732",
                    "OSV-2020-484"
                )

                it[1] should beEmpty()

                it[2] should containAll(
                    "OSV-2018-115",
                    "OSV-2018-143",
                    "OSV-2018-97",
                    "OSV-2020-484"
                )
            }
        }
    }

    "getVulnerabilitiesForIds()" should {
        "return the vulnerabilities for the given IDs" {
            val ids = setOf("GHSA-xvch-5gv4-984h", "PYSEC-2014-82")

            val result = OsvServiceWrapper().getVulnerabilitiesForIds(ids)

            result shouldBeSuccess {
                it.map { vulnerability -> vulnerability.id } shouldContainExactlyInAnyOrder ids
            }
        }
    }
})
