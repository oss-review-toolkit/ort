/*
 * Copyright (C) 2022 EPAM Systems, Inc.
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

import io.kotest.assertions.json.shouldEqualJson
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe

import kotlinx.serialization.encodeToString

import org.ossreviewtoolkit.clients.osv.OsvApiClient
import org.ossreviewtoolkit.clients.osv.OsvService
import org.ossreviewtoolkit.clients.osv.Package
import org.ossreviewtoolkit.clients.osv.VulnerabilitiesForPackageRequest
import org.ossreviewtoolkit.utils.test.getAssetAsString

private val VULNERABILITY_FOR_PACKAGE_BY_COMMIT_REQUEST = VulnerabilitiesForPackageRequest(
    commit = "6879efc2c1596d11a6a6ad296f80063b558d5e0f"
)
private val VULNERABILITY_FOR_PACKAGE_BY_NAME_AND_VERSION = VulnerabilitiesForPackageRequest(
    pkg = Package(
        name = "jinja2",
        ecosystem = "PyPI"
    ),
    version = "2.4.1"
)
private val VULNERABILITY_FOR_PACKAGE_BY_INVALID_COMMIT_REQUEST = VulnerabilitiesForPackageRequest(
    commit = "6879efc2c1596d11a6a6ad296f80063b558d5e0c"
)

class OsvServiceFunTest : StringSpec({
    "getVulnerabilitiesForPackage() returns the expected vulnerability when queried by commit" {
        val expectedResult = getAssetAsString("vulnerabilities-by-commit-expected-result.json")

        val result = OsvService().getVulnerabilitiesForPackage(VULNERABILITY_FOR_PACKAGE_BY_COMMIT_REQUEST)

        result.shouldBeSuccess {
            OsvApiClient.JSON.encodeToString(it) shouldEqualJson expectedResult
        }
    }

    "getVulnerabilitiesForPackage() returns the expected vulnerability when queried by name and version" {
        val expectedResult = getAssetAsString("vulnerabilities-by-name-and-version-expected-result.json")

        val result = OsvService().getVulnerabilitiesForPackage(VULNERABILITY_FOR_PACKAGE_BY_NAME_AND_VERSION)

        result.shouldBeSuccess {
            OsvApiClient.JSON.encodeToString(it) shouldEqualJson expectedResult
        }
    }

    "getVulnerabilityIdsForPackages() return the vulnerability IDs for the given batch request" {
        val requests = listOf(
            VULNERABILITY_FOR_PACKAGE_BY_COMMIT_REQUEST,
            VULNERABILITY_FOR_PACKAGE_BY_INVALID_COMMIT_REQUEST,
            VULNERABILITY_FOR_PACKAGE_BY_NAME_AND_VERSION
        )

        val result = OsvService().getVulnerabilityIdsForPackages(requests)

        result.shouldBeSuccess {
            it shouldBe listOf(
                listOf("OSV-2020-484"),
                emptyList(),
                listOf(
                    "PYSEC-2014-8",
                    "PYSEC-2014-82",
                    "PYSEC-2019-217",
                    "PYSEC-2019-220",
                    "PYSEC-2021-66"
                )
            )
        }
    }

    "getVulnerabilityForId() returns the expected vulnerability for the given ID" {
        val expectedResult = getAssetAsString("vulnerability-by-id-expected-result.json")

        val result = OsvService().getVulnerabilityForId("GHSA-xvch-5gv4-984h")

        result.shouldBeSuccess {
            OsvApiClient.JSON.encodeToString(it) shouldEqualJson expectedResult
        }
    }

    "getVulnerabilitiesForIds() return the vulnerabilities for the given IDs" {
        val ids = setOf("GHSA-xvch-5gv4-984h", "PYSEC-2014-82")

        val result = OsvService().getVulnerabilitiesForIds(ids)

        result.shouldBeSuccess {
            it.map { it.id } shouldContainExactlyInAnyOrder ids
        }
    }
})
