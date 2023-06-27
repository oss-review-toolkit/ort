/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.advisor

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.maps.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beTheSameInstanceAs

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk

import org.ossreviewtoolkit.model.AdvisorResult
import org.ossreviewtoolkit.model.AnalyzerResult
import org.ossreviewtoolkit.model.AnalyzerRun
import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.config.AdvisorConfiguration
import org.ossreviewtoolkit.utils.test.shouldNotBeNull

class AdvisorTest : WordSpec({
    "retrieveFindings" should {
        "return the same ORT result if there is no analyzer result" {
            val provider = mockkAdviceProvider()

            val advisor = createAdvisor(listOf(provider))

            advisor.advise(OrtResult.EMPTY) should beTheSameInstanceAs(OrtResult.EMPTY)

            coVerify(exactly = 0) {
                provider.retrievePackageFindings(any())
            }
        }

        "return an ORT result with an empty advisor run if there are no packages" {
            val provider = mockkAdviceProvider()
            val originResult = createOrtResultWithPackages(emptySet())

            val advisor = createAdvisor(listOf(provider))

            val result = advisor.advise(originResult)

            result.advisor shouldNotBeNull {
                results.advisorResults should beEmpty()
            }

            coVerify(exactly = 0) {
                provider.retrievePackageFindings(any())
            }
        }

        "return the merged results of advice providers" {
            val pkg1 = createPackage(1)
            val pkg2 = createPackage(2)
            val packages = setOf(pkg1, pkg2)
            val originResult = createOrtResultWithPackages(packages)

            val advisorResult1 = mockkAdvisorResult()
            val advisorResult2 = mockkAdvisorResult()
            val advisorResult3 = mockkAdvisorResult()
            val advisorResult4 = mockkAdvisorResult()

            val provider1 = mockkAdviceProvider()
            val provider2 = mockkAdviceProvider()

            coEvery { provider1.retrievePackageFindings(packages) } returns mapOf(
                pkg1 to advisorResult1,
                pkg2 to advisorResult3
            )
            coEvery { provider2.retrievePackageFindings(packages) } returns mapOf(
                pkg1 to advisorResult2,
                pkg2 to advisorResult4
            )

            val expectedResults = mapOf(
                pkg1.id to listOf(advisorResult1, advisorResult2),
                pkg2.id to listOf(advisorResult3, advisorResult4)
            )

            val advisor = createAdvisor(listOf(provider1, provider2))

            val result = advisor.advise(originResult)

            result.advisor shouldNotBeNull {
                results.advisorResults shouldBe expectedResults
            }
        }
    }
})

/**
 * Create a test [Advisor] instance that is configured with the given [providers].
 */
private fun createAdvisor(providers: List<AdviceProvider>): Advisor {
    val advisorConfig = AdvisorConfiguration()

    val factories = providers.map { provider ->
        val factory = mockk<AdviceProviderFactory>()
        every { factory.create(advisorConfig) } returns provider
        factory
    }

    return Advisor(factories, advisorConfig)
}

/**
 * Create an [OrtResult] containing the given [packages].
 */
private fun createOrtResultWithPackages(packages: Set<Package>): OrtResult =
    OrtResult.EMPTY.copy(
        analyzer = AnalyzerRun.EMPTY.copy(
            result = AnalyzerResult(
                projects = setOf(Project.EMPTY.copy(id = Identifier.EMPTY.copy(name = "test-project"))),
                packages = packages
            )
        )
    )

/**
 * Create a test [Package] based on the given [index].
 */
private fun createPackage(index: Int): Package =
    Package.EMPTY.copy(id = Identifier.EMPTY.copy(name = "test-package$index"))

private fun mockkAdviceProvider(): AdviceProvider =
    mockk<AdviceProvider>().apply {
        every { providerName } returns "provider"
    }

private fun mockkAdvisorResult(): AdvisorResult =
    mockk<AdvisorResult>().apply {
        every { vulnerabilities } returns emptyList()
    }
