/*
 * Copyright (C) 2021 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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
import io.kotest.matchers.collections.containExactlyInAnyOrder
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.beEmpty
import io.kotest.matchers.maps.containExactly
import io.kotest.matchers.nulls.shouldNotBeNull
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
import org.ossreviewtoolkit.plugins.advisors.api.AdviceProvider
import org.ossreviewtoolkit.plugins.advisors.api.AdviceProviderFactory
import org.ossreviewtoolkit.plugins.api.PluginConfig
import org.ossreviewtoolkit.plugins.api.PluginDescriptor

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
            val ortResult = createOrtResultWithPackages(emptySet())

            val advisor = createAdvisor(listOf(provider))

            val result = advisor.advise(ortResult)

            result.advisor shouldNotBeNull {
                results should beEmpty()
            }

            coVerify(exactly = 0) {
                provider.retrievePackageFindings(any())
            }
        }

        "return the merged results of advice providers" {
            val pkg1 = createPackage(1)
            val pkg2 = createPackage(2)
            val packages = setOf(pkg1, pkg2)
            val ortResult = createOrtResultWithPackages(packages)

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

            val result = advisor.advise(ortResult)

            result.advisor shouldNotBeNull {
                results shouldBe expectedResults
            }
        }

        "continue with other providers when a provider fails to be created" {
            val pkg = createPackage(1)
            val packages = setOf(pkg)
            val ortResult = createOrtResultWithPackages(packages)

            val successfulResult = mockkAdvisorResult()
            val successfulProvider = mockkAdviceProvider("SuccessfulProvider")
            coEvery { successfulProvider.retrievePackageFindings(packages) } returns mapOf(pkg to successfulResult)

            val failingFactory = mockk<AdviceProviderFactory> {
                every { descriptor } returns PluginDescriptor("failing-provider", "FailingProvider", "", emptyList())
                every { create(any()) } throws IllegalStateException("Could not initialize provider")
            }

            val successfulFactory = mockk<AdviceProviderFactory> {
                every { create(PluginConfig(emptyMap(), emptyMap())) } returns successfulProvider
            }

            val advisor = Advisor(listOf(failingFactory, successfulFactory), AdvisorConfiguration())

            val result = advisor.advise(ortResult)

            result.advisor shouldNotBeNull {
                results should containExactly(pkg.id to listOf(successfulResult))
                providerIssues.shouldBeSingleton {
                    it.message shouldBe "Failed to create provider 'FailingProvider': IllegalStateException: Could " +
                        "not initialize provider"
                }
            }

            coVerify(exactly = 1) {
                successfulProvider.retrievePackageFindings(packages)
            }
        }

        "continue with results from other providers when a provider fails to fetch findings" {
            val pkg = createPackage(1)
            val packages = setOf(pkg)
            val ortResult = createOrtResultWithPackages(packages)

            val successfulResult = mockkAdvisorResult()

            val failingProvider = mockkAdviceProvider("FailingProvider")
            val successfulProvider = mockkAdviceProvider("SuccessfulProvider")

            coEvery { failingProvider.retrievePackageFindings(packages) } throws
                IllegalStateException("Could not query provider service")
            coEvery { successfulProvider.retrievePackageFindings(packages) } returns mapOf(pkg to successfulResult)

            val advisor = createAdvisor(listOf(failingProvider, successfulProvider))

            val result = advisor.advise(ortResult)

            result.advisor shouldNotBeNull {
                results should containExactly(pkg.id to listOf(successfulResult))
                providerIssues.shouldBeSingleton {
                    it.message shouldBe "Failed to retrieve findings via 'FailingProvider': IllegalStateException: " +
                        "Could not query provider service"
                }
            }

            coVerify(exactly = 1) {
                failingProvider.retrievePackageFindings(packages)
                successfulProvider.retrievePackageFindings(packages)
            }
        }

        "collect provider issues from all providers that fail to fetch findings" {
            val pkg = createPackage(1)
            val packages = setOf(pkg)
            val ortResult = createOrtResultWithPackages(packages)

            val failingProvider1 = mockkAdviceProvider("FailingProvider1")
            val failingProvider2 = mockkAdviceProvider("FailingProvider2")

            coEvery { failingProvider1.retrievePackageFindings(packages) } throws
                IllegalArgumentException("Failure 1")
            coEvery { failingProvider2.retrievePackageFindings(packages) } throws
                IllegalStateException("Failure 2")

            val advisor = createAdvisor(listOf(failingProvider1, failingProvider2))

            val result = advisor.advise(ortResult)

            result.advisor shouldNotBeNull {
                results should beEmpty()
                providerIssues shouldHaveSize 2

                val messages = providerIssues.map { it.message }
                messages should containExactlyInAnyOrder(
                    "Failed to retrieve findings via 'FailingProvider1': IllegalArgumentException: Failure 1",
                    "Failed to retrieve findings via 'FailingProvider2': IllegalStateException: Failure 2"
                )
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
        every { factory.create(PluginConfig(emptyMap(), emptyMap())) } returns provider
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

private fun mockkAdviceProvider(displayName: String = "Provider"): AdviceProvider =
    mockk<AdviceProvider>().apply {
        every { descriptor } returns PluginDescriptor(displayName, displayName, "", emptyList())
    }

private fun mockkAdvisorResult(): AdvisorResult =
    mockk<AdvisorResult>().apply {
        every { vulnerabilities } returns emptyList()
        every { copy(vulnerabilities = any()) } returns this
    }
