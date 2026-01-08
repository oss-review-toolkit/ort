/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

package org.ossreviewtoolkit.utils.ort

import io.kotest.core.spec.style.WordSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll

import org.ossreviewtoolkit.clients.foojay.DiscoService
import org.ossreviewtoolkit.clients.foojay.Package
import org.ossreviewtoolkit.clients.foojay.PackagesResult
import org.ossreviewtoolkit.utils.common.Os

class JavaBootstrapperTest : WordSpec({
    afterEach {
        unmockkAll()

        JavaBootstrapper.clearCache()
    }

    "installJdk" should {
        "cache the result from the disco service" {
            mockkStatic("org.ossreviewtoolkit.utils.ort.EnvironmentKt")
            val downloadDir = tempdir()
            every { ortToolsDirectory } returns downloadDir
            val distributionDir = downloadDir.resolve("jdks/${distributionPackage.distribution}/$DISTRIBUTION_VERSION")
            distributionDir.mkdirs() shouldBe true

            val discoService = createMockedDiscoService()
            val target = JavaBootstrapper.installJdk(DISTRIBUTION, DISTRIBUTION_VERSION).shouldBeSuccess()

            val target2 = JavaBootstrapper.installJdk(DISTRIBUTION, DISTRIBUTION_VERSION).shouldBeSuccess()
            target2 shouldBe target

            coVerify(exactly = 1) {
                discoService.getPackages(
                    version = DISTRIBUTION_VERSION,
                    distributions = any(),
                    architectures = any(),
                    archiveTypes = any(),
                    packageTypes = any(),
                    operatingSystems = any(),
                    libCTypes = any(),
                    releaseStatuses = any(),
                    directlyDownloadable = true,
                    latest = any(),
                    freeToUseInProduction = true
                )
            }
        }
    }
})

private const val DISTRIBUTION = "TEMURIN"
private const val DISTRIBUTION_VERSION = "21"

/** The package to be returned by the mocked disco service. */
private val distributionPackage =
    Package(
        archiveType = "TAR_GZ",
        distribution = DISTRIBUTION.lowercase(),
        jdkVersion = 21,
        distributionVersion = DISTRIBUTION_VERSION,
        operatingSystem = Os.Name.current.toString(),
        architecture = Os.Arch.current.toString(),
        packageType = "JDK",
        libCType = "GNU",
        links = org.ossreviewtoolkit.clients.foojay.Links(
            pkgDownloadRedirect = "https://example.com/download",
            pkgInfoUri = "https://example.com/info"
        )
    )

/**
 * Create a mocked [DiscoService] that is prepared to return the test [distributionPackage].
 */
private fun createMockedDiscoService(): DiscoService {
    mockkObject(DiscoService)

    val discoServiceMock = mockk<DiscoService> {
        coEvery {
            getPackages(
                version = DISTRIBUTION_VERSION,
                distributions = any(),
                architectures = any(),
                archiveTypes = any(),
                packageTypes = any(),
                operatingSystems = any(),
                libCTypes = any(),
                releaseStatuses = any(),
                directlyDownloadable = true,
                latest = any(),
                freeToUseInProduction = true
            )
        } returns PackagesResult(listOf(distributionPackage))
    }

    every { DiscoService.create() } returns discoServiceMock

    return discoServiceMock
}
